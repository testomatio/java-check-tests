package io.testomat.progressbar;

public class ProgressBar {
    private final int total;
    private final int barLength;
    private int current;
    private String taskName;

    public ProgressBar(int total, String taskName) {
        this.total = total;
        this.barLength = 50;
        this.current = 0;
        this.taskName = taskName;
    }

    public void update(int current) {
        this.current = current;
        printProgress();
    }

    public void increment() {
        this.current++;
        printProgress();
    }

    public void finish() {
        this.current = total;
        printProgress();
        System.out.println();
    }

    public int getTotal() {
        return total;
    }

    private void printProgress() {
        double percentage = (double) current / total;
        int progress = (int) (percentage * barLength);

        StringBuilder bar = new StringBuilder();
        bar.append("\r").append(taskName).append(" [");

        for (int i = 0; i < barLength; i++) {
            if (i < progress) {
                bar.append("█");
            } else {
                bar.append("░");
            }
        }

        bar.append("] ")
                .append(String.format("%.1f%%", percentage * 100))
                .append(" (")
                .append(current)
                .append("/")
                .append(total)
                .append(")");

        System.out.print(bar.toString());
        System.out.flush();
    }
}
