package utils;

import java.util.ArrayList;
import java.util.HashMap;

public class Timer {
    static class TimerRecord {
        double point;
        double duration;

        TimerRecord(double point, double duration) {
            this.point = point;
            this.duration = duration;
        }
    }
    HashMap<String, TimerRecord> timers = new HashMap<>();

    public Timer(ArrayList<String> names) {
        for (String name : names) {
            timers.put(name, new TimerRecord(0, 0));
        }
    }

    public void start(String name) {
        timers.get(name).point = System.currentTimeMillis();
    }

    public void stop(String name) {
        TimerRecord record = timers.get(name);
        record.duration += System.currentTimeMillis() - record.point;
    }

    public double getDuration(String name) {
        return timers.get(name).duration;
    }

    public void print() {
        for (String name : timers.keySet()) {
            System.out.println(name + ": " + timers.get(name).duration + "ms");
        }
    }
}
