package io.testomat.progressbar;

public class LoadingSpinner {
    private static final String[] SPINNER_CHARS = {"|", "/", "-", "\\"};
    private volatile boolean running = false;
    private Thread spinnerThread;
    private String message;

    public LoadingSpinner(String message) {
        this.message = message;
    }

    public void start() {
        if (running) {
            return;
        }

        running = true;
        spinnerThread = new Thread(() -> {
            int index = 0;
            while (running) {
                System.out.print("\r" + message + " "
                        + SPINNER_CHARS[index % SPINNER_CHARS.length]);
                System.out.flush();
                index++;

                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });

        spinnerThread.setDaemon(true);
        spinnerThread.start();
    }

    public void stop() {
        running = false;
        if (spinnerThread != null) {
            spinnerThread.interrupt();
            try {
                spinnerThread.join(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        // Clear the spinner line
        System.out.print("\r" + " ".repeat(message.length() + 2) + "\r");
        System.out.flush();
    }

    public void stopWithMessage(String completionMessage) {
        stop();
        System.out.println(completionMessage);
    }
}
