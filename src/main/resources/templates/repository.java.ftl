package ${packageName}.repository;

import ${packageName}.entity.${table.className};
<#-- import composite Id class when applicable -->
<#-- If generator supplied pkType that ends with 'Id', import it explicitly -->
<#if pkType?matches(".*Id$")>
    import ${packageName}.entity.${pkType};
</#if>
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

<#if hasSoftDelete>
    import org.springframework.data.jpa.repository.Modifying;
    import org.springframework.data.jpa.repository.Query;
</#if>

// use pkType passed in from generator
@Repository
public interface ${table.className}Repository extends JpaRepository<${table.className}, ${pkType}> {
<#if hasSoftDelete>
    @Modifying
    @Query("UPDATE ${table.className} e SET e.deletedAt = CURRENT_TIMESTAMP WHERE e.id = :id")
    void softDeleteById(${pkType} id);
</#if>
}