package com.publicissues.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.publicissues.DBConnection;
import com.publicissues.model.Department;

public class DepartmentDAO {

    // Check department existence on the caller's transaction connection.
    public boolean exists(Connection connection, int departmentId)
            throws SQLException {

        String sql = "SELECT 1 FROM departments WHERE department_id = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, departmentId);

            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    // Retrieve all departments from the database.
    public List<Department> findAll() throws SQLException {

        String sql = """
                SELECT department_id, department_name
                FROM departments
                ORDER BY department_name
                """;

        List<Department> departments = new ArrayList<>();

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {

            while (result.next()) {

                Department department = new Department();

                department.setDepartmentId(
                        result.getInt("department_id"));

                department.setDepartmentName(
                        result.getString("department_name"));

                departments.add(department);
            }
        }

        return departments;
    }
}
