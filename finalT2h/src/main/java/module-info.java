module com.example.finalt2h {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.desktop;
    requires java.sql;


    opens com.example.finalt2h to javafx.fxml;
    exports com.example.finalt2h;
}