package org.hyperlib.util;

/**
 * Based on FlickerUtilV2, but with scaling maximum wait.
 *
 * The waits can either increase with burst, or decrease.
 * Can't extend as maxWait is private.
 */
public class ScalingFlickerUtil {
    public static final float UP_RATE = 25f;  // Default: 25
    public static final float DOWN_RATE = 5f;  // Default 5
    public static final float END_PROB_PER_BURST = 0.05f;

    private float brightness;
    private float dir = 1f;
    private float wait;
    private float maxWait;
    private float waitMult;
    private boolean stopBursts = false;
    private boolean stopAll = false;

    private float angle;
    private float currMax;
    private float currDur;
    private int numBursts = 0;

    private boolean peakFrame = false;

    public ScalingFlickerUtil() {
        this(4f, 1f);
    }

    /**
     * Constructs a new scaling flicker util.
     *
     * @param maxWait   The maximum wait time in cycle 0.
     * @param waitMult  The multiplier applied to the maximum wait time each cycle.
     */
    public ScalingFlickerUtil(float maxWait, float waitMult) {
        this.maxWait = maxWait;
        this.waitMult = waitMult;
        angle = (float) Math.random() * 360f;
    }

    public float getAngle() {
        return angle;
    }

    public void newBurst() {
        currMax = 0.75f + (float) Math.random() * 0.5f;
        if (currMax > 1f) currMax = 1f;
        if (currMax < brightness) currMax = brightness;
        dir = 1f;
        currDur = 0f + (float) Math.random() * 0.5f;
        currDur *= currDur;
        currDur += 0.05f;
        numBursts++;
        peakFrame = true;
    }

    public void newWait() {
        this.maxWait *= this.waitMult;
        this.wait = (float) (0.75f + Math.random()/4f) * this.maxWait;
        this.numBursts = 0;
        this.stopBursts = false;
        this.angle = (float) Math.random() * 360f;
    }

    public void setMaxWait(float maxWait) { this.maxWait = maxWait; }
    public void setWait(float wait) { this.wait = wait; }
    public void setNumBursts(int numBursts) { this.numBursts = numBursts; }
    public boolean isPeakFrame() { return this.peakFrame; }

    public int getNumBursts() { return this.numBursts; }

    public float getWait() { return this.wait; }

    public void advance(float amount) {
        this.peakFrame = false;
        if (this.wait > 0) {
            this.wait -= amount;
            if (this.wait > 0) {
                return;
            } else {
                newBurst();
            }
        }

        if (dir > 0) {
            brightness += amount * UP_RATE;
        } else {
            brightness -= amount * DOWN_RATE;
        }

        if (brightness < 0) brightness = 0;

        if (brightness >= currMax) {
            brightness = currMax;
            dir = -1;
        }

        currDur -= amount;
        if (currDur <= 0) {
            if (!stopBursts && !stopAll) {
                if ((float) Math.random() < END_PROB_PER_BURST * (float) numBursts) {
                    stopBursts = true;
                } else {
                    newBurst();
                }
            } else if (!stopAll && brightness <= 0) {
                newWait();
            }
        }

    }

    public void stop() {
        stopAll = true;
    }
    public float getBrightness() {
        return brightness;
    }
}








