package io.deepcover.examples.demo.controller;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Demo User Servlet - DeepCover will trace this HTTP request
 * and collect line-level coverage data for all method calls.
 */
public class UserServlet extends HttpServlet {

    private UserService userService = new UserService();

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String action = req.getParameter("action");
        if (action == null) {
            action = "list";
        }

        String result;
        switch (action) {
            case "get":
                String id = req.getParameter("id");
                result = userService.getUser(id);
                break;
            case "create":
                String name = req.getParameter("name");
                String email = req.getParameter("email");
                result = userService.createUser(name, email);
                break;
            case "delete":
                String deleteId = req.getParameter("id");
                result = userService.deleteUser(deleteId);
                break;
            default:
                result = userService.listUsers();
                break;
        }

        resp.setContentType("application/json;charset=UTF-8");
        resp.getWriter().write(result);
    }
}
