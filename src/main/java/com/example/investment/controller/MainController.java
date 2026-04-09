package com.example.investment.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Label;

/**
 * FXML-контроллер главного экрана.
 *
 * Поля с аннотацией @FXML автоматически связываются с элементами из main-view.fxml
 * по совпадению fx:id.
 *
 * На текущем этапе: минимальный контроллер, только статус-строка.
 * На следующих этапах будут добавлены TableView, Charts, кнопки.
 */
public class MainController {

    @FXML
    private Label statusLabel;

    /** Вызывается JavaFX после загрузки FXML. */
    @FXML
    public void initialize() {
        statusLabel.setText("Приложение запущено");
    }
}
