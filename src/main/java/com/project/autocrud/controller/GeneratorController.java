package com.project.autocrud.controller;

import com.project.autocrud.model.TableMetadata;
import com.project.autocrud.service.CodeGeneratorService;
import com.project.autocrud.service.GeneratedFile;
import com.project.autocrud.service.SchemaAnalyzerService;
import com.project.autocrud.service.ZipService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/generate")
public class GeneratorController {

    private final SchemaAnalyzerService analyzer;
    private final CodeGeneratorService generator;
    private final ZipService zipService;

    public GeneratorController(SchemaAnalyzerService analyzer, CodeGeneratorService generator, ZipService zipService) {
        this.analyzer = analyzer;
        this.generator = generator;
        this.zipService = zipService;
    }

    @PostMapping("/upload")
    public ResponseEntity<ByteArrayResource> generateFromUpload(
            @RequestParam("sql") MultipartFile sqlFile,
            @RequestParam(value = "overrides", required = false) MultipartFile overridesFile,
            @RequestParam("project-name") String projectName) throws Exception {

        String sql = new String(sqlFile.getBytes(), StandardCharsets.UTF_8);
        String overrides = overridesFile != null ? new String(overridesFile.getBytes(), StandardCharsets.UTF_8) : null;

        List<TableMetadata> tables = analyzer.analyze(sql, overrides);
        List<GeneratedFile> files = generateAllFiles(tables, "com.generated", projectName);
        byte[] zipBytes = zipService.createZip(files);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("auto-crud-" + LocalDate.now() + ".zip")
                .build());
        headers.setContentLength(zipBytes.length);

        ByteArrayResource resource = new ByteArrayResource(zipBytes);

        return new ResponseEntity<>(resource, headers, HttpStatus.OK);
    }

    private List<GeneratedFile> generateAllFiles(List<TableMetadata> tables, String packageName, String projectName) throws Exception {
        List<GeneratedFile> files = new ArrayList<>();

        for (TableMetadata table : tables) {
            // Skip pure join tables (they are modelled as ManyToMany on other entities)
            if (table.isJoinTablePure()) continue;

            // Entity
            files.add(new GeneratedFile(
                    "src/main/java/" + packageName.replace(".", "/") + "/entity/" + table.getClassName() + ".java",
                    generator.generateEntity(table, packageName)
            ));

            // If composite primary key (and not a pure join table), generate embeddable Id class
            if (table.isCompositePrimaryKey() && !table.isJoinTable()) {
                String idClassPath = "src/main/java/" + packageName.replace(".", "/") + "/entity/" + table.getClassName() + "Id.java";
                StringBuilder idSrc = new StringBuilder();
                idSrc.append("package ").append(packageName).append(".entity;\n\n");
                idSrc.append("import jakarta.persistence.*;\n");
                idSrc.append("import lombok.*;\n");
                idSrc.append("import java.io.Serializable;\n");
                boolean needBigDecimal = table.getColumns().stream()
                        .filter(c -> table.getPrimaryKeyColumns().contains(c.getFieldName()))
                        .anyMatch(c -> c.getJavaType() != null && c.getJavaType().equals(java.math.BigDecimal.class));
                boolean needTime = table.getColumns().stream()
                        .filter(c -> table.getPrimaryKeyColumns().contains(c.getFieldName()))
                        .anyMatch(c -> c.getJavaType() != null && java.time.temporal.Temporal.class.isAssignableFrom(c.getJavaType()));
                boolean needUUID = table.getColumns().stream()
                        .filter(c -> table.getPrimaryKeyColumns().contains(c.getFieldName()))
                        .anyMatch(c -> c.getJavaType() != null && c.getJavaType().equals(java.util.UUID.class));
                if (needBigDecimal) idSrc.append("import java.math.BigDecimal;\n");
                if (needTime) idSrc.append("import java.time.*;\n");
                if (needUUID) idSrc.append("import java.util.UUID;\n");

                idSrc.append("\n@Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode\n");
                idSrc.append("@Embeddable\n");
                idSrc.append("public class ").append(table.getClassName()).append("Id implements Serializable {\n\n");
                idSrc.append("    private static final long serialVersionUID = 1L;\n\n");

                for (String pkField : table.getPrimaryKeyColumns()) {
                    table.getColumns().stream()
                            .filter(c -> c.getFieldName().equals(pkField))
                            .findFirst()
                            .ifPresent(c -> {
                                String typeName = c.getJavaType() != null ? c.getJavaType().getSimpleName() : "Long";
                                idSrc.append("    @Column(name = \"").append(c.getName()).append("\")\n");
                                idSrc.append("    private ").append(typeName).append(" ").append(c.getFieldName()).append(";\n\n");
                            });
                }

                idSrc.append("}\n");

                files.add(new GeneratedFile(idClassPath, idSrc.toString()));
            }

            // Repository
            files.add(new GeneratedFile(
                    "src/main/java/" + packageName.replace(".", "/") + "/repository/" + table.getClassName() + "Repository.java",
                    generator.generateRepository(table, packageName)
            ));

            // Service
            files.add(new GeneratedFile(
                    "src/main/java/" + packageName.replace(".", "/") + "/service/" + table.getClassName() + "Service.java",
                    generator.generateService(table, packageName)
            ));

            // Controller
            files.add(new GeneratedFile(
                    "src/main/java/" + packageName.replace(".", "/") + "/controller/" + table.getClassName() + "Controller.java",
                    generator.generateController(table, packageName)
            ));


        }

        // Thêm pom.xml
        files.add(new GeneratedFile("pom.xml", generatePom(tables)));

        // Thêm application.yml
        files.add(new GeneratedFile("src/main/resources/application.yml", generateApplicationYml()));

        // Thêm report
        files.add(new GeneratedFile("generation-report.txt", generateReport(tables)));

        // Thêm main class
        files.add(new GeneratedFile("src/main/java/" + packageName.replace(".", "/") + "/" + toPascalCase(projectName) + "Application.java", generateMainClass(packageName, toPascalCase(projectName))
        ));

        // Maven Wrapper (copy from resources)
        files.add(new GeneratedFile("mvnw", zipService.loadResource("wrapper/mvnw")));
        files.add(new GeneratedFile("mvnw.cmd", zipService.loadResource("wrapper/mvnw.cmd")));
        files.add(new GeneratedFile(".mvn/wrapper/maven-wrapper.properties",
                zipService.loadResource("wrapper/maven-wrapper.properties")));

        return files;
    }

    private String generatePom(List<TableMetadata> tables) {
        // TODO: Consider passing projectName from the request
        String projectName = tables.isEmpty() ? "AutoCRUD" : toPascalCase(tables.get(0).getClassName()) + "Crud";
        String artifactId = projectName.toLowerCase().replaceAll("[^a-z0-9-]", "-");
        String description = "Auto-generated Spring Boot CRUD for " +
                tables.stream()
                        .map(t -> t.getTableName())
                        .collect(Collectors.joining(", "));

        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>
                
                    <parent>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-parent</artifactId>
                        <version>3.3.1</version> <!-- Or a more recent stable version -->
                        <relativePath/>
                    </parent>
                
                    <groupId>com.generated</groupId>
                    <artifactId>%s</artifactId>
                    <version>1.0.0</version>
                    <name>%s</name>
                    <description>%s</description>
                
                    <properties>
                        <java.version>21</java.version>
                        <maven.compiler.source>21</maven.compiler.source>
                        <maven.compiler.target>21</maven.compiler.target>
                        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
                    </properties>
                
                    <dependencies>
                        <!-- Spring Boot Web + JPA + Validation -->
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-web</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-data-jpa</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-validation</artifactId>
                        </dependency>
                
                        <!-- Lombok -->
                        <dependency>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                            <optional>true</optional>
                        </dependency>
                        <!-- PostgreSQL -->
                        <dependency>
                                    <groupId>org.postgresql</groupId>
                                    <artifactId>postgresql</artifactId>
                                    <scope>runtime</scope>
                        </dependency>
                
                    </dependencies>
                
                    <build>
                        <plugins>
                            <plugin>
                                <groupId>org.springframework.boot</groupId>
                                <artifactId>spring-boot-maven-plugin</artifactId>
                                <configuration>
                                    <excludes>
                                        <exclude>
                                            <groupId>org.projectlombok</groupId>
                                            <artifactId>lombok</artifactId>
                                        </exclude>
                                    </excludes>
                                </configuration>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """.formatted(artifactId, projectName, description).trim();
    }

    private String generateApplicationYml() {
        return """
                spring:
                  datasource:
                    url: jdbc:postgresql://localhost:5432/yourdb
                    username: postgres
                    password: password
                  jpa:
                    hibernate:
                      ddl-auto: update
                    show-sql: true
                """;
    }

    private String generateReport(List<TableMetadata> tables) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== AUTO CRUD GENERATION REPORT ===\n");
        sb.append("Generated on: ").append(LocalDate.now()).append("\n\n");
        for (TableMetadata t : tables) {
            sb.append("Table: ").append(t.getTableName()).append(" → ").append(t.getClassName()).append("\n");
            t.getWarnings().forEach(w -> sb.append("[WARNING] ").append(w).append("\n"));
        }
        return sb.toString();
    }

    private String generateMainClass(String packageName, String appName) {
        return """
                package %s;
                
                import org.springframework.boot.SpringApplication;
                import org.springframework.boot.autoconfigure.SpringBootApplication;
                
                @SpringBootApplication
                public class %sApplication {
                    public static void main(String[] args) {
                        SpringApplication.run(%sApplication.class, args);
                    }
                }
                """.formatted(packageName, appName, appName);
    }

    private String toPascalCase(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

}
