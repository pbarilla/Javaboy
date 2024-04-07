package com.pat;

import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;
import java.util.Date;

import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.glfw.GLFW.glfwPollEvents;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.NULL;

public class Gameboy {
    private final Memory memory = new Memory(); // Includes cartridge
    private final CPU cpu = new CPU();
    private final Controller controller = new Controller();
    private long window;
    private static final int SCREEN_WIDTH = 160;
    private static final int SCREEN_HEIGHT = 144;
    private long bootTime = new Date().getTime();
    private static double VERT_SYNC = 59.73; // aka fps
    private double SKIP_TICKS = 1000 / VERT_SYNC;
    private long nextGameTick;
    private int[][] screenBuffer = new int[256][256];
    public int[][] screenData = new int [160][144];
    private int scrollX, scrollY;
    private int wndPosX, wndPosY;

    public Gameboy() {
        // do something to setup things...
        memory.loadTestRom();
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
        if ( !glfwInit() )
            throw new IllegalStateException("Unable to initialize GLFW");

        // Configure GLFW
        glfwDefaultWindowHints(); // optional, the current window hints are already the default
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE); // the window will stay hidden after creation
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE); // the window will be resizable

        // Create the window
        window = glfwCreateWindow(SCREEN_WIDTH, SCREEN_HEIGHT, "Javaboi", NULL, NULL);
        if ( window == NULL )
            throw new RuntimeException("Failed to create the GLFW window");

        // Set up a key callback. It will be called every time a key is pressed, repeated or released.
        glfwSetKeyCallback(window, (window, key, scancode, action, mods) -> {
            if ( key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE )
                glfwSetWindowShouldClose(window, true); // We will detect this in the rendering loop
        });

        // Get the thread stack and push a new frame
        try ( MemoryStack stack = stackPush() ) {
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

    private void loop() throws InterruptedException {
        // This line is critical for LWJGL's interoperation with GLFW's
        // OpenGL context, or any context that is managed externally.
        // LWJGL detects the context that is current in the current thread,
        // creates the GLCapabilities instance and makes the OpenGL
        // bindings available for use.
        GL.createCapabilities();

        // Set the clear color
        glClearColor(1.0f, 0.0f, 0.0f, 0.0f);

        glPointSize(10.0f); // pixel size.

        long sleeptime = 0;

        // Run the rendering loop until the user has attempted to close
        // the window or has pressed the ESCAPE key.
        while ( !glfwWindowShouldClose(window) ) {
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT); // clear the framebuffer
            glLoadIdentity();

            glColor3f(0.25f,0.25f,0.0f); //blue color

            glBegin(GL_LINE_LOOP);//start drawing a line loop
            glVertex3f(-1.0f,0.0f,0.0f);//left of window
            glVertex3f(0.0f,-1.0f,0.0f);//bottom of window
            glVertex3f(1.0f,0.0f,0.0f);//right of window
            glVertex3f(0.0f,1.0f,0.0f);//top of window
            glEnd();//end drawing of line loop

            glColor3f(0.0f,1.0f,0.0f);

            glBegin(GL_LINE_LOOP);//start drawing a line loop
            glVertex3f(1.0f,0.0f,0.0f);//left of window
            glVertex3f(1.0f,1.0f,0.0f);//bottom of window
            glVertex3f(1.0f,0.0f,0.0f);//right of window
            glVertex3f(0.0f,1.0f,0.0f);//top of window
            glEnd();//end drawing of line loop

            glColor3f(0.0f,0.5f,1.0f);

            glBegin(GL_POINTS); //starts drawing of points
            glVertex3f(0.0f, 0.0f, 0.0f); // centre

            glVertex3f(-1.0f, 0.0f, 0.0f); // left centre
            glEnd();//end drawing of points

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
