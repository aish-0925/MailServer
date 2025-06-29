import com.sun.net.httpserver.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLEncoder;
import java.nio.file.*;
import java.util.*;


public class MailServer {
    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);

        server.createContext("/", new FileHandler("web"));
        server.createContext("/send", new SendHandler());
        server.createContext("/inbox", new InboxHandler());
        server.createContext("/register", new RegisterHandler());  
        server.createContext("/login", new LoginHandler()); 
        server.createContext("/reply", new ReplyHandler());  
        server.createContext("/deleteMail", new DeleteHandler());  

        server.setExecutor(null);
        server.start();
        System.out.println("Server started at http://localhost:8000");
    }

    static class FileHandler implements HttpHandler {
        String baseDir;

        FileHandler(String baseDir) {
            this.baseDir = baseDir;
        }

        public void handle(HttpExchange exchange) throws IOException {
            String filePath = baseDir + exchange.getRequestURI().getPath();
            if (filePath.endsWith("/")) filePath += "login.html";

            File file = new File(filePath);
            if (!file.exists()) {
                String response = "404 Not Found";
                exchange.sendResponseHeaders(404, response.length());
                exchange.getResponseBody().write(response.getBytes());
                exchange.close();
                return;
            }

            byte[] response = Files.readAllBytes(file.toPath());
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        }
    }

    static class SendHandler implements HttpHandler {
    public void handle(HttpExchange exchange) throws IOException {
        if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            File file = new File("web/mail.html");
            byte[] bytes = Files.readAllBytes(file.toPath());
            exchange.getResponseHeaders().add("Content-Type", "text/html");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        } else if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), "utf-8");
            BufferedReader br = new BufferedReader(isr);
            StringBuilder buf = new StringBuilder();
            int b;
            while ((b = br.read()) != -1) {
                buf.append((char) b);
            }
            Map<String, String> params = parseQuery(buf.toString());
            String from = params.get("from");
            String to = params.get("to");
            String cc = params.get("cc");
            String bcc = params.get("bcc");
            String subject = params.get("subject");
            String message = params.get("message");

            saveMail(to, from, cc, bcc, subject, message);

            // Redirect to the inbox of sender
            redirect(exchange, "/inbox?user=" + from);
        }
    }

    private void saveMail(String to, String from, String cc, String bcc, String subject, String message) throws IOException {
        // Create directory for the receiver if not exists
        File userInbox = new File("mails/" + to);
        if (!userInbox.exists()) {
            userInbox.mkdirs();
        }

        // Save mail in a text file
        String fileName = "mail_" + System.currentTimeMillis() + ".txt";
        File mailFile = new File(userInbox, fileName);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(mailFile))) {
            writer.write("From: " + from + "\n");
            writer.write("To: " + to + "\n");
            writer.write("CC: " + cc + "\n");
            writer.write("BCC: " + bcc + "\n");
            writer.write("Subject: " + subject + "\n");
            writer.write("Message:\n" + message + "\n");
        }
    }

    private Map<String, String> parseQuery(String query) {
        Map<String, String> result = new HashMap<>();
        for (String param : query.split("&")) {
            String[] entry = param.split("=");
            if (entry.length > 1) {
                result.put(entry[0], decode(entry[1]));
            }
        }
        return result;
    }

    private String decode(String value) {
        try {
            return java.net.URLDecoder.decode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return value;
        }
    }

    private void redirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().add("Location", location);
        exchange.sendResponseHeaders(302, -1);
        exchange.close();
    }
}


    static class InboxHandler implements HttpHandler {
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            String response = "<h3>Error: Only GET method allowed</h3>";
            sendResponse(exchange, 405, response);
            return;
        }

        String query = exchange.getRequestURI().getQuery();
        Map<String, String> params = parseQuery(query);
        String username = params.get("user");

        if (username == null || username.isEmpty()) {
            String response = "<h3>Error: No user provided</h3>";
            sendResponse(exchange, 400, response);
            return;
        }

        File userDir = new File("mails/" + username);
        if (!userDir.exists()) {
            userDir.mkdirs();
        }

        File[] mails = userDir.listFiles();
        StringBuilder response = new StringBuilder();
        response.append("<html><body>");
        response.append("<h2>Inbox for ").append(username).append("</h2>");

        if (mails != null && mails.length > 0) {
            for (File mail : mails) {
                List<String> lines = Files.readAllLines(mail.toPath());
                String from = "", subject = "";
                for (String line : lines) {
                    if (line.startsWith("From: ")) from = line.substring(6).trim();
                    if (line.startsWith("Subject: ")) subject = line.substring(9).trim();
                }

                response.append("<hr><pre>")
                        .append(Files.readString(mail.toPath()))
                        .append("</pre>")
                        .append("<a href='/reply?to=").append(URLEncoder.encode(from, "UTF-8"))
                        .append("&subject=").append(URLEncoder.encode(subject, "UTF-8"))
                        .append("&from=").append(URLEncoder.encode(username, "UTF-8"))
                        .append("'>Reply</a> | ")
                        .append("<a href='/deleteMail?id=").append(username).append("/").append(mail.getName())
                        .append("'>Delete</a><br>");
            }
        } else {
            response.append("<p>No messages found.</p>");
        }

        response.append("<br><a href='/send'>Compose New Mail</a>");
        response.append("</body></html>");

        sendResponse(exchange, 200, response.toString());
    }

    private Map<String, String> parseQuery(String query) {
        Map<String, String> result = new HashMap<>();
        if (query != null && !query.isEmpty()) {
            for (String param : query.split("&")) {
                String[] entry = param.split("=");
                if (entry.length > 1) {
                    result.put(entry[0], entry[1]);
                }
            }
        }
        return result;
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String responseText) throws IOException {
        byte[] bytes = responseText.getBytes("UTF-8");
        exchange.getResponseHeaders().add("Content-Type", "text/html");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}


// ReplyHandler for handling email replies
static class ReplyHandler implements HttpHandler {
    public void handle(HttpExchange exchange) throws IOException {
        if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            String query = exchange.getRequestURI().getQuery();
            Map<String, String> params = parseQuery(query);
            String to = params.get("to");
            String subject = params.get("subject");
            String from = params.get("from");

            if (to == null || subject == null || from == null) {
                String response = "<h3>Error: Missing required fields</h3>";
                sendResponse(exchange, 400, response);
                return;
            }

            // Decode the parameters
            to = java.net.URLDecoder.decode(to, "UTF-8");
            subject = java.net.URLDecoder.decode(subject, "UTF-8");
            from = java.net.URLDecoder.decode(from, "UTF-8");

            StringBuilder response = new StringBuilder();
            response.append("<html><body>");
            response.append("<h3>Replying to: ").append(to).append("</h3>");
            response.append("<form method='POST' action='/send'>");
            response.append("To: <input type='text' name='to' value='").append(to).append("' readonly><br>");
            response.append("Subject: <input type='text' name='subject' value='Re: ").append(subject).append("'><br>");
            response.append("Message:<br><textarea name='message'></textarea><br>");
            response.append("<input type='hidden' name='from' value='").append(from).append("'>");
            response.append("<input type='submit' value='Send Reply'>");
            response.append("</form>");
            response.append("</body></html>");

            sendResponse(exchange, 200, response.toString());
        }
    }

    private Map<String, String> parseQuery(String query) {
        Map<String, String> result = new HashMap<>();
        if (query != null && !query.isEmpty()) {
            for (String param : query.split("&")) {
                String[] entry = param.split("=");
                if (entry.length > 1) {
                    result.put(entry[0], entry[1]);
                }
            }
        }
        return result;
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String responseText) throws IOException {
        byte[] bytes = responseText.getBytes("UTF-8");
        exchange.getResponseHeaders().add("Content-Type", "text/html");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}


    // DeleteHandler for handling email deletion
static class DeleteHandler implements HttpHandler {
    public void handle(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        Map<String, String> params = parseQuery(query);
        String id = params.get("id");

        if (id == null || id.isEmpty()) {
            String response = "<h3>Error: No mail ID provided</h3>";
            exchange.getResponseHeaders().add("Content-Type", "text/html");
            exchange.sendResponseHeaders(400, response.length());
            exchange.getResponseBody().write(response.getBytes());
            exchange.close();
            return;
        }

        // Assuming mails are stored with the file name as the mail ID (e.g., mail_12345.txt)
        File mailFile = new File("mails/" + id);
        if (mailFile.exists()) {
            mailFile.delete();
            String response = "Mail deleted!";
            exchange.sendResponseHeaders(200, response.length());
            exchange.getResponseBody().write(response.getBytes());
        } else {
            String response = "Mail not found!";
            exchange.sendResponseHeaders(404, response.length());
            exchange.getResponseBody().write(response.getBytes());
        }
        exchange.close();
    }

    private Map<String, String> parseQuery(String query) {
        Map<String, String> result = new HashMap<>();
        if (query != null && !query.isEmpty()) {
            for (String param : query.split("&")) {
                String[] entry = param.split("=");
                if (entry.length > 1) {
                    result.put(entry[0], entry[1]);
                }
            }
        }
        return result;
    }
}

    // RegisterHandler for handling user registration
static class RegisterHandler implements HttpHandler {
    public void handle(HttpExchange exchange) throws IOException {
        if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            // Serve register.html file
            File file = new File("web/register.html");
            byte[] bytes = Files.readAllBytes(file.toPath());
            exchange.getResponseHeaders().add("Content-Type", "text/html");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        } else if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            // Handle registration
            InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), "utf-8");
            BufferedReader br = new BufferedReader(isr);
            StringBuilder buf = new StringBuilder();
            int b;
            while ((b = br.read()) != -1) {
                buf.append((char) b);
            }

            Map<String, String> params = parseQuery(buf.toString());
            String username = params.get("username");
            String password = params.get("password");

            boolean success = DatabaseManager.registerUser(username, password);

            if (success) {
                redirect(exchange, "/login"); 
            } else {
                String response = "Username already exists!";
                exchange.sendResponseHeaders(409, response.length());
                exchange.getResponseBody().write(response.getBytes());
                exchange.close();
            }
        }
    }

    private Map<String, String> parseQuery(String query) {
        Map<String, String> result = new HashMap<>();
        for (String param : query.split("&")) {
            String[] entry = param.split("=");
            if (entry.length > 1) {
                result.put(entry[0], entry[1]);
            }
        }
        return result;
    }

    private void redirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().add("Location", location);
        exchange.sendResponseHeaders(302, -1);
        exchange.close();
    }
}


    // LoginHandler for handling user login
   static class LoginHandler implements HttpHandler {
    public void handle(HttpExchange exchange) throws IOException {
        if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            // Serve login.html file
            File file = new File("web/login.html");
            byte[] bytes = Files.readAllBytes(file.toPath());
            exchange.getResponseHeaders().add("Content-Type", "text/html");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        } else if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            // Handle login
            InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), "utf-8");
            BufferedReader br = new BufferedReader(isr);
            StringBuilder buf = new StringBuilder();
            int b;
            while ((b = br.read()) != -1) {
                buf.append((char) b);
            }

            Map<String, String> params = parseQuery(buf.toString());
            String username = params.get("username");
            String password = params.get("password");

            boolean loginSuccess = DatabaseManager.authenticateUser(username, password);

            if (loginSuccess) {
                redirect(exchange, "/inbox?user=" + username); // After login, go to inbox
            } else {
                String response = "Invalid username or password!";
                exchange.sendResponseHeaders(401, response.length());
                exchange.getResponseBody().write(response.getBytes());
                exchange.close();
            }
        }
    }

    private Map<String, String> parseQuery(String query) {
        Map<String, String> result = new HashMap<>();
        for (String param : query.split("&")) {
            String[] entry = param.split("=");
            if (entry.length > 1) {
                result.put(entry[0], entry[1]);
            }
        }
        return result;
    }

    private void redirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().add("Location", location);
        exchange.sendResponseHeaders(302, -1);
        exchange.close();
    }
}

}
