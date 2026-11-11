package org.autojs.autojs.external.tile;

import androidx.annotation.NonNull;

import com.stardust.view.accessibility.NodeInfo;

import org.autojs.autojs.ui.floating.FullScreenFloatyWindow;
import org.autojs.autojs.ui.floating.layoutinspector.LayoutHierarchyFloatyWindow;

public class LayoutHierarchyTile extends LayoutInspectTileService {
    @NonNull
    @Override
    protected FullScreenFloatyWindow onCreateWindow(NodeInfo capture) {
        return new LayoutHierarchyFloatyWindow(capture) {
            @Override
            public void close() {
                super.close();
                inactive();
            }
        };
    }
}
