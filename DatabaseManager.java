import java.io.*;

public class DatabaseManager {
    private static final String USERS_FILE = "users.txt";

    // Register a new user
    public static boolean registerUser(String username, String password) {
        try {
            if (userExists(username)) {
                return false; // User already exists
            }
            BufferedWriter writer = new BufferedWriter(new FileWriter(USERS_FILE, true));
            writer.write(username + ":" + password);
            writer.newLine();
            writer.close();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    // Authenticate user during login
    public static boolean authenticateUser(String username, String password) {
        try {
            BufferedReader reader = new BufferedReader(new FileReader(USERS_FILE));
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(":");
                if (parts.length == 2 && parts[0].equals(username) && parts[1].equals(password)) {
                    reader.close();
                    return true;
                }
            }
            reader.close();
            return false;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    // Check if a user already exists in the system
    private static boolean userExists(String username) throws IOException {
        File file = new File(USERS_FILE);
        if (!file.exists()) {
            return false;
        }
        BufferedReader reader = new BufferedReader(new FileReader(file));
        String line;
        while ((line = reader.readLine()) != null) {
            String[] parts = line.split(":");
            if (parts.length > 0 && parts[0].equals(username)) {
                reader.close();
                return true;
            }
        }
        reader.close();
        return false;
    }
}
