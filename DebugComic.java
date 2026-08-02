import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class DebugComic {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:postgresql://tokaido.proxy.rlwy.net:32484/railway?options=-c%20TimeZone=UTC";
        String user = "postgres";
        String password = "VCtvhCjSrtirwBdyYPjBmyizrYGbgiSz";
        try (Connection conn = DriverManager.getConnection(url, user, password);
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT id, title, comic_id FROM chapters WHERE id = '019fb3ba-799b-7e5d-9b26-2fcc702fb565'");
            if (rs.next()) {
                System.out.println("Found in chapters: " + rs.getString("title"));
            } else {
                System.out.println("Not found in chapters.");
            }
            
            rs = stmt.executeQuery("SELECT id, title, chapter_id FROM submissions WHERE id = '019fb3ba-799b-7e5d-9b26-2fcc702fb565'");
            if (rs.next()) {
                System.out.println("Found in submissions: " + rs.getString("title") + ", chapter_id: " + rs.getString("chapter_id"));
            } else {
                System.out.println("Not found in submissions.");
            }
            
            rs = stmt.executeQuery("SELECT id, title FROM comics WHERE id = '019fb3ba-799b-7e5d-9b26-2fcc702fb565'");
            if (rs.next()) {
                System.out.println("Found in comics: " + rs.getString("title"));
            } else {
                System.out.println("Not found in comics.");
            }
        }
    }
}
