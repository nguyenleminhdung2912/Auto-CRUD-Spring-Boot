<#-- src/main/resources/templates/entity.java.ftl -->

package ${packageName}.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.*;
<#-- BigDecimal import if needed -->
<#if hasBigDecimal>
    import java.math.BigDecimal;
</#if>

<#-- AUDIT -->
<#assign hasAudit = table.hasAudit || table.columns?filter(c -> c.isCreatedAt() || c.isUpdatedAt())?has_content />
<#if hasAudit>
    import org.springframework.data.annotation.CreatedDate;
    import org.springframework.data.annotation.LastModifiedDate;
    import org.springframework.data.jpa.domain.support.AuditingEntityListener;
</#if>

<#-- ENUMS -->
<#list table.columns as col>
    <#if col.enumClass??>
        import ${packageName}.entity.${col.enumClass};
        <#break>
    </#if>
</#list>

<#-- RELATIONSHIPS: need List import if any OneToMany -->
<#assign hasOneToMany = table.relationships?filter(r -> r.relationshipType?matches("(?i)OneToMany"))?has_content />
<#if hasOneToMany>
    import java.util.List;
    import java.util.ArrayList;
</#if>

<#-- IMPORT TARGET ENTITIES -->
<#list table.relationships as rel>
    import ${packageName}.entity.${rel.targetClass};
</#list>

<#-- COMPOSITE ID -->
<#if table.compositePrimaryKey && !table.joinTable>
    import ${packageName}.entity.${table.className}Id;
</#if>

@Entity
@Table(name = "${table.tableName}")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
<#if hasAudit>
    @EntityListeners(AuditingEntityListener.class)
</#if>
public class ${table.className} {

<#-- === JOIN TABLE WITH COMPOSITE PK HANDLING === -->
<#if table.compositePrimaryKey && table.joinTable>
    <#-- For join tables, generate @Id on each ManyToOne FK field (no EmbeddedId) -->
    <#list table.relationships as rel>
        <#-- find corresponding column -->
        <#assign fkCol = table.columns?filter(c -> c.name == rel.fkColumn)?first>
        <#if fkCol?exists && fkCol.primaryKey>
            @Id
            @ManyToOne
            @JoinColumn(name = "${rel.fkColumn}")
            private ${rel.targetClass} ${rel.fieldName};
        </#if>
    </#list>
<#else>
    <#-- If this is a pure join table (two PK FKs, no payload), we won't generate entity; handled elsewhere. -->
    <#-- For composite PK with payload, generate EmbeddedId and use @MapsId on FK relationships that are part of the PK -->
    <#if table.compositePrimaryKey && !table.joinTablePure>
        @EmbeddedId
        private ${table.className}Id id;
    <#else>
        <#-- === COMPOSITE PK === -->
        <#if table.compositePrimaryKey>
            @EmbeddedId
            private ${table.className}Id id;
        <#else>
            <#list table.columns as col>
                <#if col.isPrimaryKey()>
                    @Id
                    @GeneratedValue(strategy = GenerationType.IDENTITY)
                    private ${col.javaType.simpleName} ${col.fieldName};
                    <#break>
                </#if>
            </#list>
        </#if>
    </#if>
</#if>

<#-- === CỘT THƯỜNG (BỎ FK) === -->
<#list table.columns as col>
    <#if col.isPrimaryKey() || col.isForeignKey()><#continue></#if>
    <#if col.isCreatedAt()>
        @CreatedDate
        @Column(updatable = false)
        private LocalDateTime ${col.fieldName};
    <#elseif col.isUpdatedAt()>
        @LastModifiedDate
        private LocalDateTime ${col.fieldName};
    <#elseif col.isDeletedAt()>
        private LocalDateTime ${col.fieldName};
    <#elseif col.enumClass??>
        @Enumerated(EnumType.STRING)
        private ${col.enumClass} ${col.fieldName};
    <#elseif col.isJson()>
        @Column(columnDefinition = "json")
        private String ${col.fieldName};
    <#elseif col.javaType.simpleName == "JsonNode">
        <#-- fallback, but SchemaAnalyzer should convert JsonNode to String -->
        @Column(columnDefinition = "json")
        private String ${col.fieldName};
    <#elseif col.javaType.simpleName == "BigDecimal">
        private BigDecimal ${col.fieldName};
    <#else>
        @Column(name = "${col.name}"<#if !col.isNullable()>, nullable = false</#if><#if col.isUnique()>, unique = true</#if>)
        private ${col.javaType.simpleName} ${col.fieldName};
    </#if>

</#list>

<#-- === RELATIONSHIPS === -->
<#list table.relationships as rel>
    <#-- find corresponding column for possible @MapsId usage -->
    <#assign fkCol = table.columns?filter(c -> c.name == rel.fkColumn)?first>

    <#-- Skip relationships already rendered as @Id in join-table composite case -->
    <#if table.compositePrimaryKey && table.joinTable>
        <#-- check if this rel was emitted above: find fk column and if it's pk, skip -->
        <#if fkCol?exists && fkCol.primaryKey>
            <#-- already rendered as @Id ManyToOne above -->
            <#continue>
        </#if>
    </#if>

    <#if rel.relationshipType?matches("(?i)ManyToOne")>
        <#-- If this FK is part of the composite PK and we're using EmbeddedId, map it to the embeddable id -->
        <#if table.compositePrimaryKey && !table.joinTablePure && fkCol?exists && fkCol.primaryKey>
            @MapsId("${fkCol.fieldName}")
        </#if>
        @ManyToOne<#if rel.cascade?? && rel.cascade?length > 0>(cascade = CascadeType.${rel.cascade})</#if>
        @JoinColumn(name = "${rel.fkColumn}"<#if rel.nullable == false>, nullable = false</#if>)
        private ${rel.targetClass} ${rel.fieldName};

    <#elseif rel.relationshipType?matches("(?i)OneToOne")>
        <#if rel.mappedBy?? && (rel.mappedBy?length > 0)>
            @OneToOne(mappedBy = "${rel.mappedBy}"<#if rel.cascade?? && rel.cascade?length > 0>, cascade = CascadeType.${rel.cascade}</#if>)
            private ${rel.targetClass} ${rel.fieldName};
        <#else>
            <#-- Owning OneToOne may also need @MapsId when FK is part of composite PK -->
            <#if table.compositePrimaryKey && !table.joinTablePure && fkCol?exists && fkCol.primaryKey>
                @MapsId("${fkCol.fieldName}")
            </#if>
            @OneToOne<#if rel.cascade?? && rel.cascade?length > 0>(cascade = CascadeType.${rel.cascade})</#if>
            @JoinColumn(name = "${rel.fkColumn}"<#if rel.nullable == false>, nullable = false</#if>)
            private ${rel.targetClass} ${rel.fieldName};
        </#if>

    <#elseif rel.relationshipType?matches("(?i)OneToMany")>
        <#if rel.mappedBy?? && (rel.mappedBy?length > 0)>
            @OneToMany(mappedBy = "${rel.mappedBy}"<#if rel.cascade?? && rel.cascade?length > 0>, cascade = CascadeType.${rel.cascade}</#if><#if rel.orphanRemoval>, orphanRemoval = true</#if>)
            private List<${rel.targetClass}> ${rel.fieldName} = new ArrayList<>();
        <#else>
            @OneToMany<#if rel.cascade?? && rel.cascade?length > 0>(cascade = CascadeType.${rel.cascade})</#if>
            @JoinColumn(name = "${rel.fkColumn}")
            private List<${rel.targetClass}> ${rel.fieldName} = new ArrayList<>();
        </#if>

    <#elseif rel.relationshipType?matches("(?i)ManyToMany")>
        <#if rel.mappedBy?? && (rel.mappedBy?length > 0)>
            @ManyToMany(mappedBy = "${rel.mappedBy}")
            private List<${rel.targetClass}> ${rel.fieldName} = new ArrayList<>();
        <#else>
            @ManyToMany
            @JoinTable(name = "${rel.joinTableName}",
                joinColumns = @JoinColumn(name = "${rel.joinColumn}"),
                inverseJoinColumns = @JoinColumn(name = "${rel.inverseJoinColumn}")
            )
            private List<${rel.targetClass}> ${rel.fieldName} = new ArrayList<>();
        </#if>

    <#else>
        <#-- default: treat as ManyToOne and apply @MapsId if FK is part of composite PK -->
        <#if table.compositePrimaryKey && !table.joinTablePure && fkCol?exists && fkCol.primaryKey>
            @MapsId("${fkCol.fieldName}")
        </#if>
        @ManyToOne<#if rel.cascade?? && rel.cascade?length > 0>(cascade = CascadeType.${rel.cascade})</#if>
        @JoinColumn(name = "${rel.fkColumn}"<#if rel.nullable == false>, nullable = false</#if>)
        private ${rel.targetClass} ${rel.fieldName};
    </#if>

</#list>

}