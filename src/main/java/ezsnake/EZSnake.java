package   ezsnake;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import java.io.*;
import java.util.*;

public class EZSnake   extends Application {

    // some ConstanTS for the window and how Fast the game goes
    private static final int WIDTH =     1200;
    private static final int HEIGHT = 800;
    private static final int TILE_SIZE = 20;

    // tHIS Is for speed, normal and when You go faster
    private static final int NORMAL_SPEED = 150_000_000;
    private static final int FAST_SPEED = 50_000_000;
    private static final long SPEED_UP_THRESHOLD_MS = 500; // half a Second

    // these Are the main THINGS in the Game, Like the snake and the Food
    private List<int[]> snake      = new ArrayList<>();
    private int[] food  = new int[2]; // food position
    private String direction = "RIGHT"; // Start moving Right
    private boolean gameOver = false; // if you lose, this is true
    private int score = 0; // how Many food you ate

    // stuff for User login and Score
    private String username = "";
    private String password = "";
    private int highScore = 0;

    // for timing, Drawing, and game State
    private long lastUpdate = 0;
    private Canvas canvas;
    private GraphicsContext gc;
    private AnimationTimer gameLoop;
    private boolean waitingToStart = false; // if Waiting for player To Start

    // stuff for Keeping track of key presses for Speed boost
    private final Map<KeyCode, Long> keyPressedTimes = new HashMap<>();
    private final Map<KeyCode, Boolean> speedUpActive = new HashMap<>();
    private final Set<KeyCode> directionKeys = Set.of(KeyCode.UP, KeyCode.DOWN, KeyCode.LEFT, KeyCode.RIGHT);

    @Override
    public void start(Stage primaryStage) {
        // If NOT logged in, show Login first
        if (username.isEmpty()) {
            showLoginScreen(primaryStage);
        } else {
            showGameScreen(primaryStage);
        }
    }

    // tHiS is the login And register screen
    private void showLoginScreen(Stage primaryStage) {
        VBox loginBox    = new VBox(10);
        loginBox.setStyle("-fx-padding: 20; -fx-alignment: center;");

        TextField usernameField = new TextField();
        usernameField.setPromptText("Username");
        usernameField.setMaxWidth(200);

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Password");
        passwordField.setMaxWidth(200);

        Button loginButton = new Button("Login");
        Button registerButton = new Button("Register");
        Label messageLabel  = new Label();
        Label titleLabel = new Label("EZSnake"); // game Name
        titleLabel.setStyle("-fx-font-size: 24; -fx-font-weight: bold;");

        // login button Action
        loginButton.setOnAction(e -> {
            username = usernameField.getText().trim();
            password = passwordField.getText();
            if (authenticateUser(username, password)) {
                messageLabel.setText("Login successful!");
                loadHighScore();
                showMainMenu(primaryStage);
            } else {
                messageLabel.setText("Invalid username or password.");
            }
        });

        // REgister button Action
        registerButton.setOnAction(e -> {
            username = usernameField.getText().trim();
            password = passwordField.getText();
            if (registerUser(username, password)) {
                messageLabel.setText("Registration successful. Please login.");
            } else {
                messageLabel.setText("User already exists.");
            }
        });

        loginBox.getChildren().addAll(titleLabel, usernameField, passwordField, loginButton, registerButton, messageLabel);
        Scene loginScene = new Scene(loginBox, 300, 250);
        primaryStage.setScene(loginScene);
        primaryStage.setTitle("EZSnake - Login/Register");
        primaryStage.show();
    }

    //this is just the Main menu After you log in
    private void showMainMenu(Stage primaryStage) {
        VBox menuBox = new VBox(15);
        menuBox.setStyle("-fx-padding: 20; -fx-alignment: center;");

        Label welcomeLabel = new Label("Welcome, " + username + "!");
        welcomeLabel.setStyle("-fx-font-size: 20; -fx-font-weight: bold;");

        Button startButton = new Button("Start Game");
        Button logoutButton = new Button("Logout");

        // Button to start the game
        startButton.setOnAction(e -> showGameScreen(primaryStage));
        // Button to logout and go back to login
        logoutButton.setOnAction(e -> {
            username = "";
            password = "";
            showLoginScreen(primaryStage);
        });

        menuBox.getChildren().addAll(welcomeLabel, startButton, logoutButton);

        Scene menuScene = new Scene(menuBox, 400, 300);
        primaryStage.setScene(menuScene);
        primaryStage.setTitle("EZSnake - Main Menu");
        primaryStage.show();
    }

    //thIS shows the actual Game window
    private void showGameScreen(Stage primaryStage) {
        initGame();

        canvas = new Canvas(WIDTH, HEIGHT);
        gc = canvas.getGraphicsContext2D();

        StackPane root = new StackPane(canvas);
        Scene gameScene = new Scene(root);

        waitingToStart = true;
        draw();

        keyPressedTimes.clear();
        speedUpActive.clear();

        // listen to key presses
        gameScene.setOnKeyPressed(e -> {
            if (waitingToStart) {
                if (e.getCode() == KeyCode.SPACE) {
                    waitingToStart = false;
                    gameLoop.start();
                }
                return;
            }
            if (gameOver) {
                // Restart or go back to login or quit
                if (e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.R) {
                    resetGame();
                } else if (e.getCode() == KeyCode.C) {
                    username = "";
                    password = "";
                    Stage stage = (Stage) canvas.getScene().getWindow();
                    showLoginScreen(stage);
                } else if (e.getCode() == KeyCode.ESCAPE) {
                    Platform.exit();
                }
                return;
            }

            // for arrow keys so you can go Faster if you hold
            if (directionKeys.contains(e.getCode())) {
                if (!keyPressedTimes.containsKey(e.getCode())) {
                    keyPressedTimes.put(e.getCode(), System.currentTimeMillis());
                }
            }

            // change direction,cant go bACK on yourself
            switch (e.getCode()) {
                case UP:
                    if (!direction.equals("DOWN")) direction = "UP";
                    break;
                case DOWN:
                    if (!direction.equals("UP")) direction = "DOWN";
                    break;
                case LEFT:
                    if (!direction.equals("RIGHT")) direction = "LEFT";
                    break;
                case RIGHT:
                    if (!direction.equals("LEFT")) direction = "RIGHT";
                    break;
            }
        });

        // if you let go of the key, stop speeding up
        gameScene.setOnKeyReleased(e -> {
            if (directionKeys.contains(e.getCode())) {
                keyPressedTimes.remove(e.getCode());
                speedUpActive.put(e.getCode(), false);
            }
        });

        // this is like the Game loop keeps things moving
        gameLoop = new AnimationTimer() {
            @Override
            public void handle(long now) {
                KeyCode currentDirectionKey = null;
                switch (direction) {
                    case "UP":    currentDirectionKey = KeyCode.UP; break;
                    case "DOWN":  currentDirectionKey = KeyCode.DOWN; break;
                    case "LEFT":  currentDirectionKey = KeyCode.LEFT; break;
                    case "RIGHT": currentDirectionKey = KeyCode.RIGHT; break;
                }

                int currentSpeed = NORMAL_SPEED;

                // if you hold down the key you go Faster after a bit
                if (currentDirectionKey != null && keyPressedTimes.containsKey(currentDirectionKey)) {
                    long pressedTime = System.currentTimeMillis() - keyPressedTimes.get(currentDirectionKey);
                    if (pressedTime >= SPEED_UP_THRESHOLD_MS) {
                        speedUpActive.put(currentDirectionKey, true);
                    }
                    if (Boolean.TRUE.equals(speedUpActive.get(currentDirectionKey))) {
                        currentSpeed = FAST_SPEED;
                    }
                }

                // update game every so often
                if (!waitingToStart && now - lastUpdate >= currentSpeed) {
                    update();
                    draw();
                    lastUpdate = now;
                } else if (waitingToStart) {
                    draw();
                }
            }
        };

        primaryStage.setScene(gameScene);
        primaryStage.setTitle("EZSnake - " + username + " - High Score: " + highScore);
        primaryStage.show();
    }

    //resets everything for a new Game
    private void initGame() {
        snake.clear();
        // start in the Middle!
        snake.add(new int[]{WIDTH / (2 * TILE_SIZE), HEIGHT / (2 * TILE_SIZE)});
        direction = "RIGHT";
        gameOver = false;
        score = 0;
        spawnFood();
    }

    // if you want to Play again call This
    private void resetGame() {
        initGame();
        waitingToStart = true;
        draw();
        keyPressedTimes.clear();
        speedUpActive.clear();
    }

    // THIS is the Main logic for moving the Snake and eating food etc
    private void update() {
        if (gameOver) return;

        int[] head = snake.get(0);
        int newX = head[0];
        int newY = head[1];

        // Move the snake head
        switch (direction) {
            case "UP": newY--; break;
            case "DOWN": newY++; break;
            case "LEFT": newX--; break;
            case "RIGHT": newX++; break;
        }

        // if you hit the wall, that's Game over
        if (newX < 0 || newX >= WIDTH / TILE_SIZE ||
                newY < 0 || newY >= HEIGHT / TILE_SIZE) {
            handleGameOver();
            return;
        }

        // add new head position
        snake.add(0, new int[]{newX, newY});

        // if you eat the Food
        if (newX == food[0] && newY == food[1]) {
            score++;
            spawnFood();
        } else {
            // if Not, just move forward (remove Tail)
            snake.remove(snake.size() - 1);
        }

        // if you run into Yourself, also game Over
        int[] headSegment = snake.get(0);
        for (int i = 1; i < snake.size(); i++) {
            int[] segment = snake.get(i);
            if (headSegment[0] == segment[0] && headSegment[1] == segment[1]) {
                handleGameOver();
                return;
            }
        }
    }

    // draws everything: Background, snake, food, text
    private void draw() {
        // background is dark gray, Looks nice
        gc.setFill(Color.web("#444444"));
        gc.fillRect(0, 0, WIDTH, HEIGHT);

        // snake head is red, Body is green
        for (int i = 0; i < snake.size(); i++) {
            int[] segment = snake.get(i);
            if (i == 0) {
                gc.setFill(Color.RED);
            } else {
                gc.setFill(Color.GREEN);
            }
            gc.fillRect(segment[0] * TILE_SIZE, segment[1] * TILE_SIZE,
                    TILE_SIZE - 1, TILE_SIZE - 1);
        }

        // food is Orange
        gc.setFill(Color.ORANGE);
        gc.fillOval(food[0] * TILE_SIZE, food[1] * TILE_SIZE,
                TILE_SIZE - 1, TILE_SIZE - 1);

        // score at the Top left
        gc.setFill(Color.WHITE);
        gc.setFont(javafx.scene.text.Font.font(16));
        gc.fillText("Score: " + score + " | High Score: " + highScore, 10, 20);

        // show message if Waiting to start or Game over
        if (waitingToStart) {
            gc.setFont(javafx.scene.text.Font.font(24));
            gc.setFill(Color.LIGHTGREEN);
            String startText = "Press SPACE to start";
            double textWidth = gc.getFont().getSize() * startText.length() * 0.5;
            gc.fillText(startText, (WIDTH - textWidth) / 2, HEIGHT / 2);
        } else if (gameOver) {
            gc.setFont(javafx.scene.text.Font.font(24));
            gc.setFill(Color.WHITE);
            String gameOverText = "Game Over! Press ENTER or R to restart, C to change user, ESC to exit";
            double textWidth = gc.getFont().getSize() * gameOverText.length() * 0.5;
            gc.fillText(gameOverText,
                    (WIDTH - textWidth) / 2, HEIGHT / 2);
        }
    }

    // this Puts the Food somewhere not on the Snake
    private void spawnFood() {
        Random rand = new Random();
        boolean validPosition = false;

        while (!validPosition) {
            food[0] = rand.nextInt(WIDTH / TILE_SIZE);
            food[1] = rand.nextInt(HEIGHT / TILE_SIZE);
            validPosition = true;

            for (int[] segment : snake) {
                if (food[0] == segment[0] && food[1] == segment[1]) {
                    validPosition = false;
                    break;
                }
            }
        }
    }

    // what happens when you Lose
    private void handleGameOver() {
        gameOver = true;
        gameLoop.stop();

        // if you broke your High Score save
        if (score > highScore) {
            highScore = score;
            updateHighScore();
        }
    }

    // checks if Username and password are in users.txt
    private boolean authenticateUser(String username, String password) {
        File file = new File("users.txt");
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length >= 3 && parts[0].equals(username) && parts[1].equals(password)) {
                    highScore = Integer.parseInt(parts[2]);
                    return true;
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading users: " + e.getMessage());
        }
        return false;
    }

    // adds a new user to users.txt if username not used
    private boolean registerUser(String username, String password) {
        File file = new File("users.txt");
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts[0].equals(username)) {
                    return false;
                }
            }
        } catch (IOException ignored) {}

        try (PrintWriter writer = new PrintWriter(new FileWriter(file, true))) {
            writer.println(username + "," + password + ",0");
            return true;
        } catch (IOException e) {
            System.err.println("Error registering user: " + e.getMessage());
            return false;
        }
    }

    // loads the High score for the current user
    private void loadHighScore() {
        File file = new File("users.txt");
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length >= 3 && parts[0].equals(username)) {
                    highScore = Integer.parseInt(parts[2]);
                    return;
                }
            }
        } catch (IOException e) {
            System.err.println("Error loading high score: " + e.getMessage());
        }
        highScore = 0;
    }

    // updates the High score in users.txt if you beat it
    private void updateHighScore() {
        File file = new File("users.txt");
        List<String> lines = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts[0].equals(username)) {
                    lines.add(username + "," + password + "," + highScore);
                } else {
                    lines.add(line);
                }
            }
        } catch (IOException e) {
            System.err.println("Error updating high score: " + e.getMessage());
            return;
        }

        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            for (String line : lines) {
                writer.println(line);
            }
        } catch (IOException e) {
            System.err.println("Error writing high score: " + e.getMessage());
        }
    }

    // this is Just the Main thing to launch the Game
    public static void main(String[] args) {
        launch(args);
    }
}