package hageldave.jplotter.coordsys.timelabels.units;

import hageldave.jplotter.util.Pair;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicReference;

public class YearTimeUnit implements ITimeUnit {
    private final static long differenceInMillis = 31536000000L;

    private final static double lowerDifferenceLimit = 0.05;

    @Override
    public LocalDateTime floor(LocalDateTime value) {
        return value.truncatedTo(ChronoUnit.YEARS);
    }

    @Override
    public LocalDateTime increment(LocalDateTime value, double delta) {
        if (delta % 1 == 0) {
            return value.plusYears((long) delta);
        } else {
            return value.plus((long) (YearTimeUnit.differenceInMillis * delta), ChronoUnit.MILLIS);
        }
    }


    public Pair<double[], String> convertTicks(ITimeUnit timeUnit, double[] ticks, AtomicReference<Double> multiplier) {
        double difference = ticks[1]-ticks[0];
        double[] convertedTicks = new double[ticks.length];
        String unitLabel;

        if (difference < lowerDifferenceLimit) {
            for (int i = 0; i < ticks.length; i++)
                convertedTicks[i] = ticks[i]*360.0;
            timeUnit = new HourTimeUnit();
            multiplier.set(multiplier.get()/360.0);
            Pair<double[], String> convertedTickPair = timeUnit.convertTicks(timeUnit, convertedTicks, multiplier);
            return new Pair<>(convertedTickPair.first, convertedTickPair.second);

        } else {
            convertedTicks = ticks;
            unitLabel = timeUnit.getUnitLabel();
        }

        return new Pair<>(convertedTicks, unitLabel);
    }

    @Override
    public String getUnitLabel() {
        return "y";
    }
}
