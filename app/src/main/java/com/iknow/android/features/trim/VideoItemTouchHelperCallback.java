package com.iknow.android.features.trim;

import android.content.Context;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.iknow.android.R;

public class VideoItemTouchHelperCallback extends ItemTouchHelper.Callback {
    private final TrimmerAdapter mAdapter;
    private final int reducedWidth;
    private final int animationDuration = 300; // Duration in milliseconds

    public VideoItemTouchHelperCallback(TrimmerAdapter adapter, Context context) {
        mAdapter = adapter;
        // Convert 100 dp to pixels
        float scale = context.getResources().getDisplayMetrics().density;
        this.reducedWidth = (int) (100 * scale + 0.5f); // 100 dp in pixels
    }

    @Override
    public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
        int dragFlags = ItemTouchHelper.UP | ItemTouchHelper.DOWN | ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT;
        return makeMovementFlags(dragFlags, 0); // No swipe action, only drag
    }

    @Override
    public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
        mAdapter.onItemMove(viewHolder.getAdapterPosition(), target.getAdapterPosition());
        return true;
    }

    @Override
    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
        // No swipe action
    }

    @Override
    public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
        if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
            // Store original width for dragged item
            ViewGroup.LayoutParams params = viewHolder.itemView.getLayoutParams();
            int originalWidth = params.width; // Get the original width
            viewHolder.itemView.setTag(R.id.original_width, originalWidth); // Store original width as tag

            // Change alpha and width of dragged item
            mAdapter.animateItemAlpha((TrimmerAdapter.ViewHolder) viewHolder, 0.5f, animationDuration);
            mAdapter.animateItemWidth((TrimmerAdapter.ViewHolder) viewHolder, originalWidth, reducedWidth, animationDuration);

        }
        super.onSelectedChanged(viewHolder, actionState);
    }

    @Override
    public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
        super.clearView(recyclerView, viewHolder);
        // Reset alpha and width of dragged item and width of other items
        int originalWidth = (int) viewHolder.itemView.getTag(R.id.original_width); // Retrieve original width
        mAdapter.animateItemAlpha((TrimmerAdapter.ViewHolder) viewHolder, 1f, animationDuration);
        mAdapter.animateItemWidth((TrimmerAdapter.ViewHolder) viewHolder, reducedWidth, originalWidth, animationDuration);
        mAdapter.resetAllItemsWidth(originalWidth, animationDuration);
    }
}
