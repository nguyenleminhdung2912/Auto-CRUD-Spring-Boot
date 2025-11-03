<#-- src/main/resources/templates/service.java.ftl -->

package ${packageName}.service;

import ${packageName}.entity.${table.className};
<#-- import composite Id if pkType is Id class -->
<#if pkType?matches(".*Id$")>
    import ${packageName}.entity.${pkType};
</#if>
import ${packageName}.repository.${table.className}Repository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// pkType and pkFieldName are passed from generator
<#-- pkType = "OrderItemsId" or "Long" etc. -->
<#-- pkFieldName = field name for PK in entity, e.g., "id" or "orderId" -->

@Service
@RequiredArgsConstructor
@Transactional
public class ${table.className}Service {

private final ${table.className}Repository repository;

public List<${table.className}> findAll() {
return repository.findAll();
}

public ${table.className} findById(${pkType} id) {
return repository.findById(id).orElseThrow();
}

public ${table.className} save(${table.className} entity) {
return repository.save(entity);
}

public ${table.className} update(${pkType} id, ${table.className} entity) {
<#if pkType?matches(".*Id$")>
    // composite id
    entity.setId(id);
<#else>
    // single-column id — set field directly
    entity.set${pkFieldName?cap_first}(id);
</#if>
return repository.save(entity);
}

public void deleteById(${pkType} id) {
repository.deleteById(id);
}
}