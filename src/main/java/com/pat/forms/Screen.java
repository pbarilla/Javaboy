package com.pat.forms;

import javax.swing.*;

public class Screen extends JFrame {
    private JPanel contentPane;
    private JButton loadTestRomButton;
    private JTextPane textPane1;

    public Screen() {
        setTitle("Javaboi");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setContentPane(contentPane);
        pack();

        // Set the frame location to the center of the screen
        setLocationRelativeTo(null);

        setVisible(true);

        textPane1.setText("Hello World, This is a test!\nNew Line\nAnother Line");

    }
}
