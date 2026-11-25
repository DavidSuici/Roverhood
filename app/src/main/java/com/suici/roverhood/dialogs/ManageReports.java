package com.suici.roverhood.dialogs;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.suici.roverhood.R;
import com.suici.roverhood.databases.FirebaseRepository;
import com.suici.roverhood.databases.LocalDatabase;
import com.suici.roverhood.fragments.RoverFeed;
import com.suici.roverhood.models.User;
import com.suici.roverhood.utils.Date;
import com.suici.roverhood.utils.NotificationTarget;

import java.util.List;
import java.util.Map;

// Reports list for organizers, moderators and admins (from the + menu): newest first.
// Tapping a report opens what was reported (same as tapping its notification): a post opens
// its author's profile with the post highlighted, a comment opens the comments on it.
// The trash icon removes a report once it was handled.
public class ManageReports extends DialogFragment {

    private final RoverFeed feed;

    private Context context;
    private FirebaseRepository firebaseRepository;
    private TextView title;
    private ProgressBar loading;
    private LinearLayout list;

    public ManageReports(RoverFeed feed) {
        this.feed = feed;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_manage_reports, container, false);
        context = requireContext();
        firebaseRepository = FirebaseRepository.getInstance(context);
        title = view.findViewById(R.id.reportsTitle);
        loading = view.findViewById(R.id.reportsLoading);
        list = view.findViewById(R.id.reportsList);
        loadReports();
        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            int height = (int) (getResources().getDisplayMetrics().heightPixels * 0.85);
            getDialog().getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, height);
        }
    }

    // Offline (the feed shows saved data): the reports saved at the last online load, read-only
    private boolean isOffline() {
        return feed != null && feed.isOfflineMode();
    }

    private void loadReports() {
        if (isOffline()) {
            loading.setVisibility(View.GONE);
            showReports(firebaseRepository.loadCachedReports());
            return;
        }
        loading.setVisibility(View.VISIBLE);
        firebaseRepository.loadReports(new FirebaseRepository.ReportsCallback() {
            @Override
            public void onLoaded(List<FirebaseRepository.Report> reports) {
                if (!isAdded()) return;
                loading.setVisibility(View.GONE);
                showReports(reports);
            }

            @Override
            public void onError(String errorMessage) {
                if (!isAdded()) return;
                loading.setVisibility(View.GONE);
                Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showReports(List<FirebaseRepository.Report> reports) {
        title.setText("Reports (" + reports.size() + ")" + (isOffline() ? " · offline" : ""));
        list.removeAllViews();
        float density = getResources().getDisplayMetrics().density;
        Map<String, User> users = LocalDatabase.getInstance(context).getAllUsers();

        for (FirebaseRepository.Report report : reports) {
            boolean isComment = report.commentId != null && !report.commentId.isEmpty();
            String reported = username(users, report.reportedUserId);
            String reporter = username(users, report.reporterId);

            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, (int) (8 * density), 0, (int) (8 * density));
            row.setBackgroundResource(android.R.drawable.list_selector_background);

            LinearLayout texts = new LinearLayout(context);
            texts.setOrientation(LinearLayout.VERTICAL);
            texts.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            TextView what = new TextView(context);
            what.setText((isComment ? "🚩 Comment by " : "🚩 Post by ") + reported);
            what.setTextSize(16);
            what.setTypeface(null, Typeface.BOLD);
            texts.addView(what);

            TextView details = new TextView(context);
            String date = report.createdAt > 0 ? " · " + Date.formatTimestamp(report.createdAt) : "";
            details.setText("Reported by " + reporter + date);
            details.setTextSize(13);
            details.setAlpha(0.7f);
            texts.addView(details);

            if (report.reason != null && !report.reason.isEmpty()) {
                TextView reason = new TextView(context);
                reason.setText("“" + report.reason + "”");
                reason.setTextSize(14);
                reason.setTypeface(null, Typeface.ITALIC);
                texts.addView(reason);
            }
            row.addView(texts);

            ImageButton delete = new ImageButton(context);
            delete.setImageResource(android.R.drawable.ic_menu_delete);
            delete.setBackgroundResource(android.R.color.transparent);
            delete.setContentDescription("Delete report");
            delete.setOnClickListener(v -> confirmDelete(report));
            if (!isOffline()) row.addView(delete);

            row.setOnClickListener(v -> {
                dismiss();
                if (feed == null) return;
                feed.openNotificationTarget(new NotificationTarget(
                        isComment ? NotificationTarget.TYPE_REPORT_COMMENT : NotificationTarget.TYPE_REPORT_POST,
                        report.postId, isComment ? report.commentId : null, null, reported));
            });
            list.addView(row);
        }

        if (reports.isEmpty()) {
            TextView empty = new TextView(context);
            empty.setText("No reports");
            empty.setAlpha(0.6f);
            empty.setPadding(0, (int) (8 * density), 0, 0);
            list.addView(empty);
        }
    }

    private static String username(Map<String, User> users, String userId) {
        User user = userId != null ? users.get(userId) : null;
        return user != null && user.getUsername() != null && !user.getUsername().isEmpty()
                ? user.getUsername() : "Unknown user";
    }

    private void confirmDelete(FirebaseRepository.Report report) {
        new AlertDialog.Builder(context)
                .setTitle("Delete report")
                .setMessage("Remove this report from the list? Do this once it was handled.")
                .setPositiveButton("Delete", (dialog, which) ->
                        firebaseRepository.deleteReport(report.key, new FirebaseRepository.PostOperationCallback() {
                            @Override
                            public void onSuccess() {
                                if (!isAdded()) return;
                                Toast.makeText(context, "Report deleted", Toast.LENGTH_SHORT).show();
                                loadReports();
                            }

                            @Override
                            public void onError(String errorMessage) {
                                if (!isAdded()) return;
                                Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show();
                            }
                        }))
                .setNegativeButton("Cancel", null)
                .show();
    }
}
