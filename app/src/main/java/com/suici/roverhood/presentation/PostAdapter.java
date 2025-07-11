package com.suici.roverhood.presentation;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ProgressBar;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.suici.roverhood.R;

import java.util.List;

public class PostAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_POST = 0;
    private static final int VIEW_TYPE_SPACE = 1;

    private final List<PostHandler> postHandlerList;
    private final Context context;

    private boolean isLoading = false;

    public PostAdapter(Context context, List<PostHandler> postHandlerList) {
        this.context = context;
        this.postHandlerList = postHandlerList;
    }

    @Override
    // Last element is reserved for a space with a loading spin
    public int getItemViewType(int position) {
        if (position == postHandlerList.size()) {
            return VIEW_TYPE_SPACE;
        } else {
            return VIEW_TYPE_POST;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_POST) {
            View view = LayoutInflater.from(context).inflate(R.layout.item_post, parent, false);
            return new PostViewHolder(view);
        } else {
            View view = LayoutInflater.from(context).inflate(R.layout.item_space, parent, false);
            return new SpaceViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof PostViewHolder) {
            PostHandler postHandler = postHandlerList.get(position);
            postHandler.loadIntoView(holder.itemView);
        }
        else if (holder instanceof SpaceViewHolder) {
            SpaceViewHolder spaceHolder = (SpaceViewHolder) holder;

            if (isLoading) {
                spaceHolder.loadingMoreProgress.setVisibility(View.VISIBLE);
            } else {
                spaceHolder.loadingMoreProgress.setVisibility(View.GONE);
            }
        }
    }

    @Override
    // Adding +1 accounting for the last space element
    public int getItemCount() {
        return postHandlerList.size() + 1;
    }

    public void setLoading(boolean loading) {
        this.isLoading = loading;

        // Updating the loading space element at the end
        notifyItemChanged(postHandlerList.size());
    }

    public static class PostViewHolder extends RecyclerView.ViewHolder {
        public PostViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }

    public static class SpaceViewHolder extends RecyclerView.ViewHolder {
        ProgressBar loadingMoreProgress;

        public SpaceViewHolder(@NonNull View itemView) {
            super(itemView);
            loadingMoreProgress = itemView.findViewById(R.id.loadingMoreProgress);
        }
    }

    // Marking the post as visible or not, for different actions in PostHandler
    @Override
    public void onViewAttachedToWindow(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewAttachedToWindow(holder);

        int position = holder.getBindingAdapterPosition();
        if (position != RecyclerView.NO_POSITION && position < postHandlerList.size() && holder instanceof PostViewHolder) {
            PostHandler postHandler = postHandlerList.get(position);
            postHandler.setPostVisible(true);
        }
    }

    @Override
    public void onViewDetachedFromWindow(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewDetachedFromWindow(holder);

        int position = holder.getBindingAdapterPosition();
        if (position != RecyclerView.NO_POSITION && position < postHandlerList.size() && holder instanceof PostViewHolder) {
            PostHandler postHandler = postHandlerList.get(position);
            postHandler.setPostVisible(false);
        }
    }

    public void detachAllViews(RecyclerView recyclerView) {
        int childCount = recyclerView.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = recyclerView.getChildAt(i);
            RecyclerView.ViewHolder holder = recyclerView.getChildViewHolder(child);

            int position = holder.getBindingAdapterPosition();
            if (position != RecyclerView.NO_POSITION && position < postHandlerList.size() && holder instanceof PostViewHolder) {
                PostHandler postHandler = postHandlerList.get(position);
                postHandler.setPostVisible(false);
            }
        }
    }
}
