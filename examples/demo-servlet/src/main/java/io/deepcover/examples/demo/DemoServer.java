package io.deepcover.examples.demo;

import io.deepcover.examples.demo.controller.UserServlet;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

/**
 * Standalone server used by the quick start and repeatable benchmark.
 */
public class DemoServer {

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getProperty("demo.port", "18080"));
        Server server = new Server(port);
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
        context.setContextPath("/demo-servlet");
        context.addServlet(new ServletHolder(new UserServlet()), "/user");
        server.setHandler(context);
        server.start();
        System.out.println("DeepCover demo listening on http://127.0.0.1:" + port + "/demo-servlet/user");
        server.join();
    }
}
