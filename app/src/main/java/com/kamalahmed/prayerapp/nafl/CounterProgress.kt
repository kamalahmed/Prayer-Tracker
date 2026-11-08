package com.kamalahmed.prayerapp.nafl

/** A completed target remains a full ring; subsequent taps start another lap, never reset history. */
data class CounterProgress(val count: Int,val target: Int,val laps: Int,val activeLap: Int,val fraction: Float) {
    val hasTarget get()=target>0
    companion object {
        fun of(count: Int,target: Int): CounterProgress {
            val n=count.coerceIn(0,1_000_000); val t=target.coerceIn(0,1_000_000)
            if(t==0) return CounterProgress(n,0,0,1,0f)
            val laps=n/t; val remainder=n%t
            return CounterProgress(n,t,laps,if(n>0 && remainder==0) laps else laps+1,
                if(n>0 && remainder==0) 1f else remainder.toFloat()/t)
        }
    }
}
