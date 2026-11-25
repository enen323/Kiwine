package com.prepdeck.common.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.springframework.util.Assert;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

public class JsonbTypeHandler extends BaseTypeHandler<Object> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final Class<?> type;

    public JsonbTypeHandler() {
        this.type = Object.class;
    }

    public JsonbTypeHandler(Class<?> type) {
        Assert.notNull(type, "Type argument cannot be null");
        this.type = type;
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Object parameter, JdbcType jdbcType) throws SQLException {
        try {
            String json = OBJECT_MAPPER.writeValueAsString(parameter);
            ps.setObject(i, json, Types.VARCHAR);
        } catch (Exception e) {
            throw new SQLException("Failed to serialize JSONB parameter", e);
        }
    }

    @Override
    public Object getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String json = rs.getString(columnName);
        return deserialize(json);
    }

    @Override
    public Object getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String json = rs.getString(columnIndex);
        return deserialize(json);
    }

    @Override
    public Object getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String json = cs.getString(columnIndex);
        return deserialize(json);
    }

    private Object deserialize(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(json, type);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize JSONB to " + type.getName() + ": " + json, e);
        }
    }
}
