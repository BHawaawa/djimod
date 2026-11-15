package com.example.avatadrone.qa;

import com.example.avatadrone.AvataDrone;
import com.example.avatadrone.client.Rc2View;
import com.example.avatadrone.client.Rc2Indicators;
import com.example.avatadrone.entity.AvataDroneEntity;
import com.example.avatadrone.item.Rc2Item;
import com.example.avatadrone.network.DroneControlPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.MovementType;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;
import java.nio.file.*;
import java.util.function.Consumer;

public final class AccidentQa implements ClientModInitializer {
    private int tick;
    private volatile Throwable failure;
    private AvataDroneEntity demo;
    private ItemStack rc;
    @Override public void onInitializeClient() {
        if (!Boolean.getBoolean("avatadrone.accidentQa")) return;
        ClientTickEvents.END_CLIENT_TICK.register(c->{
            if(c.world==null || c.player==null || c.getServer()==null)return;
            c.options.pauseOnLostFocus=false;
            if(failure!=null)throw new AssertionError("ACCIDENT_QA failed",failure);
            try{run(c);}catch(Exception e){throw new RuntimeException(e);}
        });
    }
    private void server(MinecraftClient c, Consumer<ServerPlayerEntity> task) {
        var id=c.player.getUuid();c.getServer().execute(()->{
            try{task.accept(c.getServer().getPlayerManager().getPlayer(id));}
            catch(Throwable ex){failure=ex;ex.printStackTrace();}
        });
    }
    private static void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);}
    private static DroneControlPayload command(AvataDroneEntity d,boolean arm,boolean rth){
        return new DroneControlPayload(d.getUuid(),0,0,0,0,0,2,2,arm,false,rth);
    }
    private AvataDroneEntity create(ServerPlayerEntity p,int model,double y){
        var w=p.getServerWorld();
        var d=model==0?AvataDrone.AVATA360.create(w):model==1?AvataDrone.MINI4PRO.create(w):AvataDrone.M400.create(w);
        d.refreshPositionAndAngles(0,y,0,0,0); d.setNoGravity(true);
        d.installBattery(new ItemStack(model==0?AvataDrone.AVATA360_BATTERY:model==1?AvataDrone.MINI4PRO_BATTERY:AvataDrone.M400_BATTERY));
        w.spawnEntity(d);
        rc.removeSubNbt("DroneAccident");
        rc.getOrCreateNbt().putUuid(Rc2Item.DRONE,d.getUuid());
        rc.getOrCreateNbt().putString(Rc2Item.DIMENSION,w.getRegistryKey().getValue().toString());
        d.applyPilotCommand(command(d,true,false),p);
        check(d.areMotorsArmed(),"armed model "+model);
        return d;
    }
    private void failed(ServerPlayerEntity p,AvataDroneEntity d,int reason){
        check(d.hasAccident() && d.accidentReason()==reason,"accident reason");
        check(!d.areMotorsArmed()&&!d.isReturningHome()&&!d.hasControlSignal(p),"stopped and disconnected");
        var point=d.getAccidentPosition();
        check(rc.getNbt().getCompound("DroneAccident").getDouble("X")==point.x,"exact recorded coordinate");
        d.applyPilotCommand(command(d,true,false),p);d.applyPilotCommand(command(d,false,true),p);
        check(!d.areMotorsArmed()&&!d.isReturningHome(),"cannot rearm or return");
        d.move(MovementType.SELF,new Vec3d(0,-.3,0));
        check(point.equals(d.getAccidentPosition()),"failure point does not follow wreck");
        NbtCompound nbt=new NbtCompound();d.writeCustomDataToNbt(nbt);
        var restored=(AvataDroneEntity)d.getType().create(p.getServerWorld());
        restored.readCustomDataFromNbt(nbt);
        check(restored.hasAccident()&&!restored.hasControlSignal(p)&&point.equals(restored.getAccidentPosition()),"persistent wreck");
        d.discard();
    }
    private void landingRegression(ServerPlayerEntity p) {
        try {
            var tickReturn = AvataDroneEntity.class.getDeclaredMethod("tickSmartReturn");
            tickReturn.setAccessible(true);
            var homeY = AvataDroneEntity.class.getDeclaredField("homeY"); homeY.setAccessible(true);
            var homeX = AvataDroneEntity.class.getDeclaredField("homeX"); homeX.setAccessible(true);
            var homeZ = AvataDroneEntity.class.getDeclaredField("homeZ"); homeZ.setAccessible(true);
            var w = p.getServerWorld();
            for (int model=0; model<3; model++) for (int scenario=0; scenario<4; scenario++) {
                for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)for(int y=200;y<=205;y++)
                    w.setBlockState(new BlockPos(x,y,z),Blocks.AIR.getDefaultState());
                double expected = scenario==0 ? 200 : scenario==1 ? 203 : scenario==2 ? 202.5 : 200;
                if(scenario==1 || scenario==2 || scenario==3)
                    for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)w.setBlockState(new BlockPos(x,202,z),
                            (scenario==2 ? Blocks.STONE_SLAB : Blocks.STONE).getDefaultState());
                var d=create(p,model,205);
                homeY.setDouble(d,scenario==0 ? 204 : 200); // deliberately wrong historical height
                homeX.setDouble(d,0); homeZ.setDouble(d,0);
                for(int step=0;step<800 && d.areMotorsArmed();step++) {
                    if(scenario==3 && step==10)
                        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)w.setBlockState(new BlockPos(x,202,z),Blocks.AIR.getDefaultState());
                    tickReturn.invoke(d);
                    d.move(MovementType.SELF,d.getVelocity());
                    if(d.areMotorsArmed()) check(d.getY()>=expected-.001,"no penetration");
                }
                check(!d.areMotorsArmed()&&!d.hasAccident(),"safe touchdown model="+model+" scenario="+scenario);
                check(Math.abs(d.getY()-expected)<.011,"current surface touchdown "+d.getY()+" expected "+expected);
                d.discard();
            }
            // Manual full-stick descents used to retain enough inertia to report a crash.
            for (int model=0; model<3; model++) {
                var d=create(p,model,205);
                d.setVelocity(0,-0.4,0);
                for(int step=0;step<500 && d.areMotorsArmed();step++) {
                    d.applyPilotCommand(new DroneControlPayload(d.getUuid(),0,-1,0,0,0,2,2,false,false,false),p);
                    d.tick();
                }
                check(!d.hasAccident() && !d.areMotorsArmed(), "manual safe landing model="+model);
                d.applyPilotCommand(command(d,true,false),p);
                check(d.areMotorsArmed(), "rearm after manual landing model="+model);
                d.discard();
            }
            System.out.println("LANDING_QA PASS three models: below takeoff, raised roof, half slab, ground removed during descent");
        } catch (ReflectiveOperationException ex) { throw new RuntimeException(ex); }
    }
    private void shot(MinecraftClient c,String name)throws Exception{
        Files.createDirectories(Path.of("screenshots"));
        try(var image=ScreenshotRecorder.takeScreenshot(c.getFramebuffer())){image.writeTo(Path.of("screenshots",name+".png"));}
    }
    private void run(MinecraftClient c)throws Exception{
        tick++;
        if (tick == 1 && c.currentScreen instanceof net.minecraft.client.gui.screen.ConfirmScreen) c.setScreen(null);
        if(tick==70)server(c,p->{
            var w=p.getServerWorld();
            p.teleport(w,-3,200,-3,0,0);
            for(int x=-6;x<=6;x++)for(int z=-6;z<=6;z++){
                w.setBlockState(new BlockPos(x,199,z),Blocks.STONE.getDefaultState());
                for(int y=200;y<=205;y++)w.setBlockState(new BlockPos(x,y,z),Blocks.AIR.getDefaultState());
            }
            rc=new ItemStack(AvataDrone.DJI_RC2);p.setStackInHand(Hand.MAIN_HAND,rc);
            for(int model=0;model<3;model++){
                var soft=create(p,model,200.03);soft.move(MovementType.SELF,new Vec3d(0,-.05,0));
                check(!soft.hasAccident(),"soft touchdown is safe");soft.discard();
                var hard=create(p,model,201);hard.move(MovementType.SELF,new Vec3d(0,-2,0));failed(p,hard,1);
                for(int y=200;y<=203;y++)for(int z=-2;z<=2;z++)w.setBlockState(new BlockPos(2,y,z),Blocks.STONE.getDefaultState());
                var wall=create(p,model,201);wall.move(MovementType.SELF,new Vec3d(4,0,0));failed(p,wall,1);
                for(int y=200;y<=203;y++)for(int z=-2;z<=2;z++)w.setBlockState(new BlockPos(2,y,z),Blocks.AIR.getDefaultState());
                for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)w.setBlockState(new BlockPos(x,203,z),Blocks.STONE.getDefaultState());
                var ceiling=create(p,model,201);ceiling.move(MovementType.SELF,new Vec3d(0,3,0));failed(p,ceiling,1);
                for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)w.setBlockState(new BlockPos(x,203,z),Blocks.AIR.getDefaultState());
                for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)w.setBlockState(new BlockPos(x,201,z),Blocks.WATER.getDefaultState());
                var water=create(p,model,201);water.tick();failed(p,water,2);
                for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)for(int y=200;y<=202;y++)w.setBlockState(new BlockPos(x,y,z),Blocks.AIR.getDefaultState());
            }
            landingRegression(p);
            demo=create(p,0,201);
            p.currentScreenHandler.sendContentUpdates();
            System.out.println("ACCIDENT_QA PASS all three aircraft: wall, ceiling, hard/soft landing, water, persistence, rearm/RTH rejection");
        });
        if(tick==140){c.setScreen(null);Rc2View.requestEnterFromKey();}
        if(tick==210)server(c,p->{
            demo.refreshPositionAndAngles(0,201,0,0,0);
            for(int y=200;y<=203;y++)for(int z=-2;z<=2;z++)p.getServerWorld().setBlockState(new BlockPos(2,y,z),Blocks.STONE.getDefaultState());
            demo.move(MovementType.SELF,new Vec3d(4,0,0));
        });
        if(tick==235){
            check(Rc2View.accidentReport()!=null,"report reached client");
            check(!Rc2Indicators.connected(c.player.getMainHandStack()),"red controller link");
            shot(c,"accident-disconnected-location");
        }
        if(tick==260)server(c,p->{check(!demo.isReturningHome(),"no delayed failsafe return");demo.discard();});
        if(tick==290){check(Rc2View.accidentReport()!=null,"location survives entity removal");shot(c,"accident-location-retained");}
        if(tick==310){System.out.println("ACCIDENT_QA PASS client location, red link, frozen feed and retained report");c.scheduleStop();}
    }
}
