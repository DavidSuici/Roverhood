package com.suici.roverhood.presentation;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ProgressBar;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.suici.roverhood.R;
import com.suici.roverhood.utils.FiltersManager;

import java.util.List;

public class PostAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_POST = 0;
    private static final int VIEW_TYPE_SPACE = 1;
    private static final int VIEW_TYPE_HEADER = 2;

    private final List<PostHandler> postHandlerList;
    private final Context context;

    private boolean isLoading = false;
    private int filtersHeight = 0;

    public PostAdapter(Context context, List<PostHandler> postHandlerList) {
        this.context = context;
        this.postHandlerList = postHandlerList;
    }

    @Override
    // Last element is reserved for a space with a loading spin
    public int getItemViewType(int position) {
        if (position == 0) {
            return VIEW_TYPE_HEADER;
        } else if (position == postHandlerList.size() + 1) {
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
        } else if (viewType == VIEW_TYPE_HEADER) {
            View view = LayoutInflater.from(context).inflate(R.layout.item_header, parent, false);
            return new HeaderViewHolder(view);
        } else {
            View view = LayoutInflater.from(context).inflate(R.layout.item_space, parent, false);
            return new SpaceViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof PostViewHolder) {
            PostHandler postHandler = postHandlerList.get(position-1);
            postHandler.loadIntoView(holder.itemView);
            // An announcement shown on screen counts as seen (announcements button count)
            if (postHandler.getPost().isAnnouncement()
                    && com.suici.roverhood.utils.AnnouncementsSeen.markSeen(context, postHandler.getPost().getId())
                    && onAnnouncementSeen != null) {
                onAnnouncementSeen.run();
            }
        }
        else if (holder instanceof HeaderViewHolder) {
            HeaderViewHolder headerHolder = (HeaderViewHolder) holder;
            if(FiltersManager.showInfoBar(context)) {
                ViewGroup.LayoutParams params = headerHolder.spaceFilters.getLayoutParams();
                params.height = filtersHeight;
                headerHolder.spaceFilters.setLayoutParams(params);

                headerHolder.spaceFilters.setVisibility(View.VISIBLE);
            }
            else
                headerHolder.spaceFilters.setVisibility(View.GONE);

            headerHolder.noPosts.setVisibility(showNoPosts ? View.VISIBLE : View.GONE);
            if (headerBinder != null) headerBinder.bindHeader(headerHolder.itemView);
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

    // Lets the feed fill the header (e.g. the team panel) every time it is bound
    public interface HeaderBinder {
        void bindHeader(View header);
    }

    private HeaderBinder headerBinder;

    public void setHeaderBinder(HeaderBinder headerBinder) {
        this.headerBinder = headerBinder;
    }

    public static class HeaderViewHolder extends RecyclerView.ViewHolder {
        View spaceFilters;
        View noPosts;

        public HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            spaceFilters = itemView.findViewById(R.id.spaceFilters);
            noPosts = itemView.findViewById(R.id.headerNoPosts);
        }
    }

    @Override
    // Adding +2 accounting for the first and last space element
    public int getItemCount() {
        return postHandlerList.size() + 2;
    }

    private Runnable onAnnouncementSeen;

    public void setOnAnnouncementSeen(Runnable onAnnouncementSeen) {
        this.onAnnouncementSeen = onAnnouncementSeen;
    }

    // "No posts" under the header panels when the filters leave nothing to show
    private boolean showNoPosts = false;

    public void setShowNoPosts(boolean show) {
        if (showNoPosts == show) return;
        showNoPosts = show;
        notifyItemChanged(0);
    }

    public void setLoading(boolean loading) {
        this.isLoading = loading;

        // Updating the loading space element at the end
        notifyItemChanged(postHandlerList.size() + 1);
    }

    public void setFiltersHeight(int height) {
        this.filtersHeight = height;

        // Updating the space element at the start
        notifyItemChanged(0);
    }

    public static class PostViewHolder extends RecyclerView.ViewHolder {
        public PostViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }

    public static class SpaceViewHolder extends RecyclerView.ViewHolder {
        ProgressBar loadingMoreProgress;
        View spaceFilters;

        public SpaceViewHolder(@NonNull View itemView) {
            super(itemView);
            loadingMoreProgress = itemView.findViewById(R.id.loadingMoreProgress);
            spaceFilters = itemView.findViewById(R.id.spaceFilters);
        }
    }

    // Marking the post as visible or not, for different actions in PostHandler
    @Override
    public void onViewAttachedToWindow(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewAttachedToWindow(holder);

        int position = holder.getBindingAdapterPosition();
        if (position != RecyclerView.NO_POSITION && position <= postHandlerList.size() && holder instanceof PostViewHolder) {
            PostHandler postHandler = postHandlerList.get(position-1);
            postHandler.setPostVisible(true);
        }
    }

    @Override
    public void onViewDetachedFromWindow(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewDetachedFromWindow(holder);

        int position = holder.getBindingAdapterPosition();
        if (position != RecyclerView.NO_POSITION && position <= postHandlerList.size() && holder instanceof PostViewHolder) {
            PostHandler postHandler = postHandlerList.get(position-1);
            postHandler.setPostVisible(false);
        }
    }

    public void detachAllViews(RecyclerView recyclerView) {
        int childCount = recyclerView.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = recyclerView.getChildAt(i);
            RecyclerView.ViewHolder holder = recyclerView.getChildViewHolder(child);

            int position = holder.getBindingAdapterPosition();
            if (position != RecyclerView.NO_POSITION && position <= postHandlerList.size() && holder instanceof PostViewHolder) {
                PostHandler postHandler = postHandlerList.get(position-1);
                postHandler.setPostVisible(false);
            }
        }
    }
}
