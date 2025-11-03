package com.project.autocrud.parser;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.create.table.CreateTable;
import java.util.List;

public class SqlParser {
    public static List<CreateTable> parse(String sql) throws JSQLParserException {
        var statements = CCJSqlParserUtil.parseStatements(sql);
        return statements.stream()
                .filter(s -> s instanceof CreateTable)
                .map(s -> (CreateTable) s)
                .toList();
    }
}
