package com.pat;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.IOException;
import java.util.Date;

public class Gameboy extends JPanel {
    private final Memory memory = new Memory(); // Includes cartridge
    private final CPU cpu = new CPU(memory);
    private final Controller controller = new Controller();

    private static final int SCREEN_WIDTH = 512;
    private static final int SCREEN_HEIGHT = 512;
    private long bootTime = new Date().getTime();
    private static final double VERT_SYNC = 59.73; // aka fps
    private double SKIP_TICKS = 1000 / VERT_SYNC;
    private long nextGameTick;

    private int[][] screenBuffer = new int[256][256]; // internal buffer
    public int[][] screenData = new int[160][144]; // GameBoy screen dimensions

    private int scrollX, scrollY;
    private int wndPosX, wndPosY;

    private JFrame frame;
    private JTextArea registerTextArea;

    public Gameboy() throws IOException {
        // Initialize JFrame for rendering
        frame = new JFrame("Javaboiiiiii Emulator");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(SCREEN_WIDTH + 300, SCREEN_HEIGHT); // adding 300 for the side panel
        frame.add(this); // Add this JPanel (Gameboy) as the rendering surface
        frame.setVisible(true);

        // Input listeners for AWT
        frame.addKeyListener(new KeyListener() {
            @Override
            public void keyPressed(KeyEvent e) {
                // You can handle key presses similar to the GLFW key handling
                if (e.getKeyCode() == KeyEvent.VK_P) {
                    System.out.println("Pause button pressed!");
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
                System.out.printf("KeyReleased %s\n", e.getKeyChar());
            }

            @Override
            public void keyTyped(KeyEvent e) {
                System.out.printf("KeyTyped %s\n", e.getKeyChar());
            }
        });

        JPanel sidePanel = createSidePanel();

        // Add components to the frame
        frame.setLayout(new BorderLayout());
        frame.add(this, BorderLayout.CENTER);        // Main rendering area goes in the center
        frame.add(sidePanel, BorderLayout.EAST);     // Side panel goes on the right

        frame.setVisible(true);
        // Dynamic updates to registers (for demo purposes)
        Timer registerUpdateTimer = new Timer(10, e -> updateRegisterView());
        registerUpdateTimer.start();

        nextGameTick = System.currentTimeMillis();
    }

    public JPanel createSidePanel() {
        JPanel sidePanel = new JPanel();
        sidePanel.setLayout(new BorderLayout());

        // Title for the register panel
        JLabel titleLabel = new JLabel("GameBoy Registers");
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 16));

        // Text area for displaying register information
        registerTextArea = new JTextArea();
        registerTextArea.setEditable(false); // Read-only
        registerTextArea.setFont(new Font("Monospaced", Font.PLAIN, 14)); // Monospaced for alignment
        registerTextArea.setText("Registers will appear here..."); // Initial placeholder text

        // Scroll pane for the text area (in case register info overflows)
        JScrollPane scrollPane = new JScrollPane(registerTextArea);

        // Add components to the side panel
        sidePanel.add(titleLabel, BorderLayout.NORTH);
        sidePanel.add(scrollPane, BorderLayout.CENTER);

        return sidePanel;
    }

    /**
     * Updates the content of the register view. This method is called periodically.
     * Replace the dummy data here with actual CPU register state.
     */
    private void updateRegisterView() {
        // Example of register data (replace with actual CPU register values)
        StringBuilder registerInfo = new StringBuilder();

        Register.RegisterHash registerHash = cpu.sampleRegisters();
        Flags flags = cpu.getFlags();

        // Dummy data for demonstration
        registerInfo.append("AF: ").append(String.format("0x%04X", registerHash.AF.getReg())).append("\n");
        registerInfo.append("BC: ").append(String.format("0x%04X", registerHash.BC.getReg())).append("\n");
        registerInfo.append("DE: ").append(String.format("0x%04X", registerHash.DE.getReg())).append("\n");
        registerInfo.append("HL: ").append(String.format("0x%04X", registerHash.HL.getReg())).append("\n");
        registerInfo.append("SP: ").append(String.format("0x%04X", cpu.getSP())).append("\n");
        registerInfo.append("PC: ").append(String.format("0x%04X", cpu.getPC())).append("\n");
        registerInfo.append("\nFlags:\n");
        registerInfo.append("Z: ").append(flags.isSet(Flags.Flag.ZERO)).append("\n");
        registerInfo.append("N: ").append(flags.isSet(Flags.Flag.SUBTRACT)).append("\n");
        registerInfo.append("H: ").append(flags.isSet(Flags.Flag.HALF_CARRY)).append("\n");
        registerInfo.append("C: ").append(flags.isSet(Flags.Flag.CARRY)).append("\n");

        // Update the text area with the new register data
        registerTextArea.setText(registerInfo.toString());
    }


    public void startScreen() throws InterruptedException {
        // Triggered to start the rendering process
        Timer timer = new Timer((int) SKIP_TICKS, e -> repaint());
        timer.start();
        loop();
    }


    private void loop() throws InterruptedException {
        int cycles = 0;
        boolean shouldRun = true;
        while (shouldRun) {
            cycles = cycles + cpu.fetchDecodeExecute();
            if (cycles > 9999) {
                shouldRun = false;
            }
        }

        prepareScreenBufferForFrame();

        System.out.println("Have finished running for 9999");


    }

    private void prepareScreenBufferForFrame() {
        int currentPixelX = 0;
        int currentPixelY = 0;
        // Do a horizontal line at a time by scanning down vertically and doing each horizontal line
        for (int yy = 0; yy < 32; yy++) {
            currentPixelY = yy * 8;
            for (int xx = 0; xx < 32; xx++) {
                currentPixelX = xx * 8;
                // do 8x8


                for (int innerY = 0; innerY < 8; innerY++) {
                    // get byte
                    int drawByteLocation = 0x8000 + (yy * xx);
                    int drawByteSecondLocation = 0x8000 + (yy * xx) + 1;
                    int drawByte = this.memory.readByteFromLocation(drawByteLocation);
                    int drawByteSecond = this.memory.readByteFromLocation(drawByteSecondLocation);

                    for (int innerX = 0; innerX < 8; innerX++) {

                        boolean topLine = Helpful.getBit(drawByte, innerX);
                        boolean bottomLine = Helpful.getBit(drawByteSecond, innerX);

                        // decide the color

                        Color color;

                        if (topLine && bottomLine) {
                            color = Color.YELLOW;
                        } else if (topLine && !bottomLine) {
                            color = Color.RED;
                        } else if (!topLine && bottomLine) {
                            color = Color.GREEN;
                        } else {
                            color = Color.BLACK;
                        }

//                        float drawX = 0 - (1 * ((float) (innerX + currentPixelX) / 256));
//                        float drawY = 0 + (1 * ((float) (innerY + currentPixelY) / 256));

                        screenBuffer[innerX + currentPixelX][innerY + currentPixelY] = color.getRGB();
                    }
                }
            }
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        // Cast to Graphics2D for more control (optional)
        Graphics2D g2d = (Graphics2D) g;

        // Clear the screen
        g2d.setColor(Color.BLACK);
        g2d.fillRect(0, 0, getWidth(), getHeight());

        // Render GameBoy screen data
        // You can adapt this to read from `screenData` or `screenBuffer`
        for (int y = 0; y < screenData.length; y++) {
            for (int x = 0; x < screenData[y].length; x++) {
                g2d.setColor(getAWTColor(screenData[y][x]));
                g2d.fillRect(x * 3, y * 3, 3, 3); // Scale-up each pixel for visibility
            }
        }
    }

    /**
     * Converts screen buffer values into AWT colors.
     */
    private Color getAWTColor(int colorValue) {
        if (colorValue < 0 || colorValue > 3) {
            System.err.println("Invalid color value: " + colorValue);
            return Color.PINK; // Return a debug color
        }

        Color[] dmgPalette = {
                new Color(155, 188, 15),  // Shade 0
                new Color(139, 172, 15),  // Shade 1
                new Color(48, 98, 48),    // Shade 2
                new Color(15, 56, 15)     // Shade 3
        };

        return dmgPalette[colorValue];
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        Gameboy gameboy = new Gameboy();
        gameboy.startScreen();
    }
}