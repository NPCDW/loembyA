
package master.flame.danmaku.danmaku.model;

public class Duration implements Cloneable {

    private long mInitialDuration;

    private float factor = 1.0f;

    public long value;

    public Duration(long initialDuration) {
        mInitialDuration = initialDuration;
        value = initialDuration;
    }
    
    public void setValue(long initialDuration) {
        mInitialDuration = initialDuration;
        value = (long) (mInitialDuration * factor);
    }

    /**
     * 无条件重算速度基准，确保 setValue 与 setFactor 的调用顺序不影响结果。
     * 两者均为低频调用，无需以 factor 是否变化作为守卫。
     */
    public void setFactor(float f) {
        factor = f;
        value = (long) (mInitialDuration * f);
    }

    /**
     * 存活寿命只认 mInitialDuration，不受滚动速度系数 factor 影响。
     * R2LDanmaku.measure() 用 value 计算速度，寿命判定使用 getLifetime()，两者独立。
     */
    public long getLifetime() {
        return mInitialDuration;
    }

}
