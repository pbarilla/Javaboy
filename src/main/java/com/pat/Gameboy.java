package com.pat;

import com.pat.forms.Screen;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;

import javax.swing.*;
import java.io.IOException;
import java.nio.IntBuffer;
import java.util.Date;

import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.NULL;

public class Gameboy {
    private final Memory memory = new Memory(); // Includes cartridge
    private final CPU cpu = new CPU(memory);
    private final Controller controller = new Controller();
    private long window;
    private static final int SCREEN_WIDTH = 512;
    private static final int SCREEN_HEIGHT = 512;
    private long bootTime = new Date().getTime();
    private static double VERT_SYNC = 59.73; // aka fps
    private double SKIP_TICKS = 1000 / VERT_SYNC;
    private long nextGameTick;
    private int[][] screenBuffer = new int[256][256];
    public int[][] screenData = new int[160][144];
    private int scrollX, scrollY;
    private int wndPosX, wndPosY;
    private final Screen screen = new Screen();

    public Gameboy() throws IOException {
        // do something to setup things...
        System.out.println("Loading test rom");
        memory.loadTestRom();
    }

    public void startScreen() {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                screen.setVisible(true);
            }
        });
    }

    private long tickCount() {
        return new Date().getTime() - this.bootTime;
    }

    public void run() throws InterruptedException {

        this.nextGameTick = tickCount();

        init();
        loop();

        // Free the window callbacks and destroy the window
        glfwFreeCallbacks(window);
        glfwDestroyWindow(window);

        // Terminate GLFW and free the error callback
        glfwTerminate();
        glfwSetErrorCallback(null).free();
    }

    private void init() {
        // Set up an error callback. The default implementation
        // will print the error message in System.err.
        GLFWErrorCallback.createPrint(System.err).set();

        // Initialize GLFW. Most GLFW functions will not work before doing this.
        if (!glfwInit())
            throw new IllegalStateException("Unable to initialize GLFW");

        // Configure GLFW
        glfwDefaultWindowHints(); // optional, the current window hints are already the default
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE); // the window will stay hidden after creation
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE); // the window will be resizable

        // Create the window
        window = glfwCreateWindow(SCREEN_WIDTH, SCREEN_HEIGHT, "Javaboi", NULL, NULL);
        if (window == NULL)
            throw new RuntimeException("Failed to create the GLFW window");

        // Set up a key callback. It will be called every time a key is pressed, repeated or released.
        glfwSetKeyCallback(window, (window, key, scancode, action, mods) -> {
            if (key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE)
                glfwSetWindowShouldClose(window, true); // We will detect this in the rendering loop
        });

        // Get the thread stack and push a new frame
        try (MemoryStack stack = stackPush()) {
            IntBuffer pWidth = stack.mallocInt(1); // int*
            IntBuffer pHeight = stack.mallocInt(1); // int*

            // Get the window size passed to glfwCreateWindow
            glfwGetWindowSize(window, pWidth, pHeight);

            // Get the resolution of the primary monitor
            GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());

            // Center the window
            glfwSetWindowPos(
                    window,
                    (vidmode.width() - pWidth.get(0)) / 2,
                    (vidmode.height() - pHeight.get(0)) / 2
            );
        } // the stack frame is popped automatically

        // Make the OpenGL context current
        glfwMakeContextCurrent(window);
        // Enable v-sync
        glfwSwapInterval(1);

        // Make the window visible
        glfwShowWindow(window);
    }

    // TODO: think of better names for the shades
    // MAX = nearly black
    // OFF = basically light green
    // default = white
    enum GBColor {
        MAX, BRIGHT, DULL, OFF
    }

    void setDrawColor(GBColor color) {
        switch (color) {
            case MAX:
                glColor3f(8.0f / 255.0f, 24.0f / 255.0f, 32.0f / 255.0f);
                break;
            case BRIGHT:
                glColor3f(52.0f / 255.0f, 104.0f / 255.0f, 86.0f / 255.0f);
                break;
            case DULL:
                glColor3f(136.0f / 255.0f, 192.0f / 255.0f, 112.0f / 255.0f);
                break;
            case OFF:
                glColor3f(224.0f / 255.0f, 248.0f / 255.0f, 208.0f / 255.0f);
                break;
            default:
                glColor3f(255.0f, 255.0f, 255.0f);
                break;
        }
    }

    private void loop() throws InterruptedException {
        // This line is critical for LWJGL's interoperation with GLFW's
        // OpenGL context, or any context that is managed externally.
        // LWJGL detects the context that is current in the current thread,
        // creates the GLCapabilities instance and makes the OpenGL
        // bindings available for use.
        GL.createCapabilities();

        // Set the clear color (red, good for debugging i guess?)
        glClearColor(1.0f, 0.0f, 0.0f, 0.0f);

        glPointSize(2.0f); // pixel size.

        long sleeptime = 0;


        // Run the rendering loop until the user has attempted to close
        // the window or has pressed the ESCAPE key.
        while (!glfwWindowShouldClose(window)) {

            int cycles = this.cpu.fetchDecodeExecute();

            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT); // clear the framebuffer
            glLoadIdentity();


            // render line at a time

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

//                        System.out.printf("printing 0x%x%x at location 0x%x and 0x%x\n", drawByte, drawByteSecond, drawByteLocation, drawByteSecondLocation);

                        for (int innerX = 0; innerX < 8; innerX++) {

                            boolean topLine = Helpful.getBit(drawByte, innerX);
                            boolean bottomLine = Helpful.getBit(drawByteSecond, innerX);

                            if (topLine && bottomLine) {
                                setDrawColor(GBColor.MAX);
                            } else if (topLine && !bottomLine) {
                                setDrawColor(GBColor.BRIGHT);
                            } else if (!topLine && bottomLine) {
                                setDrawColor(GBColor.DULL);
                            } else {
                                setDrawColor(GBColor.OFF);
                            }

                            glBegin(GL_POINTS); //starts drawing of points

                            float drawX = 0 - (1 * ((float) (innerX + currentPixelX) / 256));
                            float drawY = 0 + (1 * ((float) (innerY + currentPixelY) / 256));

//                    System.out.printf("Drawing: %f %f\n", drawX, drawY);

                            glVertex3f(drawX, drawY, 0.0f);

                            glEnd();//end drawing of points
                        }
                    }


                }
            }

//            setDrawColor(GBColor.DULL);
//            glBegin(GL_POINTS); //starts drawing of points
//            glVertex3f(0.0f, 0.0f, 0.0f);
//            glVertex3f(-1.0f, 0.0f, 0.0f);
//            glVertex3f(1.0f, 0.0f, 0.0f);
//            glEnd();//end drawing of points

//            setDrawColor(GBColor.MAX);
//            glBegin(GL_POINTS); //starts drawing of points
////            glVertex3f(1.0f, 1.0f, 0.0f); // top right
////            glVertex3f(-1.0f, 1.0f, 0.0f); // top left
////            glVertex3f(-0.99f, -0.99f, 0.0f); // bottom left
//
//
//
//            glVertex3f(-0.90f, -0.90f, 0.0f); // centre
//
//
//            glEnd();//end drawing of points

//            setDrawColor(GBColor.BRIGHT);
//            glBegin(GL_POINTS); //starts drawing of points
//            glVertex3f(0.0f, 0.0f, 0.0f);
//            glVertex3f(0.0f, 0.0f, 0.0f);
//            glVertex3f(0.0f, 0.0f, 0.0f);
//            glEnd();//end drawing of points


//            glBegin(GL_LINE_LOOP);//start drawing a line loop
//            glVertex3f(-1.0f,0.0f,0.0f);//left of window
//            glVertex3f(0.0f,-1.0f,0.0f);//bottom of window
//            glVertex3f(1.0f,0.0f,0.0f);//right of window
//            glVertex3f(0.0f,1.0f,0.0f);//top of window
//            glEnd();//end drawing of line loop

//            glColor3f(0.0f,1.0f,0.0f);

//            glBegin(GL_LINE_LOOP);//start drawing a line loop
//            glVertex3f(1.0f,0.0f,0.0f);//left of window
//            glVertex3f(1.0f,1.0f,0.0f);//bottom of window
//            glVertex3f(1.0f,0.0f,0.0f);//right of window
//            glVertex3f(0.0f,1.0f,0.0f);//top of window
//            glEnd();//end drawing of line loop

//            glColor3f(0.0f,0.5f,1.0f);
//            setDrawColor(GBColor.DULL);
//            glBegin(GL_POINTS); //starts drawing of points
//
//            glVertex3f(-0.1f, 0.5f, 0.0f);
//
//
//            glVertex3f(0.1f, 0.2f, 0.0f);
//            glVertex3f(0.3f, 0.4f, 0.0f); // centre
//
////            glVertex3f(-1.0f, 0.0f, 0.0f); // left centre
//            glEnd();//end drawing of points
//
//            setDrawColor(GBColor.BRIGHT);
//            glBegin(GL_POINTS); //starts drawing of points
//
//            glVertex3f(1.0f, 0.9f, 0.0f); // centre
//            glEnd();//end drawing of points
            glfwSwapBuffers(window); // swap the color buffers
            // Poll for window events. The key callback above will only be
            // invoked during this call.
            glfwPollEvents();

            // This'll make sure we keep to 59ish fps. defined as VERT_SYNC above.

            nextGameTick += SKIP_TICKS;
            sleeptime = nextGameTick - tickCount();
            if (sleeptime > 0) {
                Thread.sleep(sleeptime);
            } else {
                // running behind, skip frames
                // dont do anything just now though...
            }


        }
    }
}
