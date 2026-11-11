package org.autojs.autojs.external.tile;

import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.stardust.view.accessibility.NodeInfo;

import org.autojs.autojs.ui.floating.FullScreenFloatyWindow;
import org.autojs.autojs.ui.floating.layoutinspector.LayoutBoundsFloatyWindow;

public class LayoutBoundsTile extends LayoutInspectTileService {
    @NonNull
    @Override
    protected FullScreenFloatyWindow onCreateWindow(NodeInfo capture) {
        return new LayoutBoundsFloatyWindow(capture) {
            @Override
            public void close() {
                super.close();
                inactive();
            }
        };
    }
}
