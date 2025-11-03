<#-- src/main/resources/templates/controller.java.ftl -->

package ${packageName}.controller;

import ${packageName}.entity.${table.className};
import ${packageName}.service.${table.className}Service;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

<#-- Use pkType passed in from generator -->
<#if pkType?matches(".*Id$")>
    import ${packageName}.entity.${pkType};
</#if>

@RestController
@RequestMapping("/api/${table.endpointPath}")
@RequiredArgsConstructor
public class ${table.className}Controller {

private final ${table.className}Service service;

@GetMapping
public List<${table.className}> getAll() {
return service.findAll();
}

@GetMapping("/{id}")
public ${table.className} getById(@PathVariable ${pkType} id) {
return service.findById(id);
}

@PostMapping
public ${table.className} create(@RequestBody ${table.className} entity) {
return service.save(entity);
}

@PutMapping("/{id}")
public ${table.className} update(@PathVariable ${pkType} id, @RequestBody ${table.className} entity) {
return service.update(id, entity);
}

@DeleteMapping("/{id}")
public void delete(@PathVariable ${pkType} id) {
service.deleteById(id);
}
}