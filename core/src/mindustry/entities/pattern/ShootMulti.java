package mindustry.entities.pattern;

import arc.util.*;
import mindustry.entities.*;

public class ShootMulti extends ShootPattern{
    public ShootPattern source;
    public ShootPattern[] dest = {}, all = {};
    public ShootMulti(ShootPattern source, ShootPattern... dest){
        this.source = source;
        this.dest = dest;
        //to not allocate inside shoot()
        this.all = new ShootPattern[dest.length + 1];
        for(int i = 0; i < dest.length; i++){
            all[i] = dest[i];
        }
        all[dest.length] = source;
    }

    public ShootMulti(){
    }

    //deep copy needed for flips
    @Override
    public void flip(){
        source = source.copy();
        source.flip();
        dest = dest.clone();
        for(int i = 0; i < dest.length; i++){
            dest[i] = dest[i].copy();
            dest[i].flip();
        }
        all = new ShootPattern[dest.length + 1];
        for(int i = 0; i < dest.length; i++){
            all[i] = dest[i];
        }
        all[dest.length] = source;
    }

    @Override
    public void shoot(int totalShots, BulletHandler handler, @Nullable Runnable barrelIncrementer){
        int[] counter = {totalShots};

        //dest patterns first, then source last
        shootRecursive(all, 0, counter, handler, barrelIncrementer, 0f, 0f, 0f, 0f, null);
    }

    /**
     * Patterns are executed in the order they appear in the array. The last pattern in the array is applied innermost, ensuring that
     * alternating/offsetting patterns behave per bullet rather than per group.
     */
    public void shootRecursive(ShootPattern[] patterns, int idx, int[] counter, BulletHandler handler, 
        Runnable barrelIncrementer, float x, float y, float rotation, float delay, Mover move){
        if(idx == patterns.length){
            handler.shoot(x, y, rotation, delay, move);
            if(barrelIncrementer != null) barrelIncrementer.run();
            counter[0]++;
            return;
        }

        ShootPattern p = patterns[idx];
        p.shoot(counter[0], (dx, dy, dRot, dDelay, dMove) -> {
            Mover combined = null;
            if(move != null || dMove != null){
                combined = b -> {
                    if(move != null) move.move(b);
                    if(dMove != null) dMove.move(b);
                };
            }
            shootRecursive(patterns, idx + 1, counter, handler, barrelIncrementer, x + dx, y + dy, rotation + dRot, delay + dDelay, combined);
        }, barrelIncrementer);
    }
}
