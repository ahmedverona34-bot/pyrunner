package com.ahmed.pyrunner;

public class Bridge {
    public interface Listener {
        void output(String text, boolean error);
        String input(String prompt);
    }

    private final Listener listener;

    public Bridge(Listener listener) {
        this.listener = listener;
    }

    public void onOutput(String text, boolean error) {
        listener.output(text, error);
    }

    public String onInput(String prompt) {
        return listener.input(prompt);
    }
}
