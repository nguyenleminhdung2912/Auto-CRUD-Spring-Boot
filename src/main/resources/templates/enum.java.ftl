package ${packageName}.entity;

public enum ${enumName} {
<#list enumValues as val>
    ${val?upper_case}<#if val_has_next>, </#if>
</#list>
}