package com.pat;

public class Gameboy {
    // Internal Von Neumann
    private final Memory memory = new Memory(); // Includes cartridge
    private final CPU cpu = new CPU();

    // Inputs
    private final Controller controller = new Controller();

    // Outputs
    final Video video = new Video();
    private final Sound sound = new Sound();

    public Gameboy() {

    }

    public void run() {
        this.video.run();
    }
}
