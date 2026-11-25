package com.suici.roverhood.dialogs;

import android.content.Context;
import android.text.InputFilter;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.suici.roverhood.databases.FirebaseRepository;

// "Report" on a post or comment: asks for an optional reason and sends the report to the
// organizers, moderators and admins (they get a notification, see notifyReport in functions/index.js).
public class Report {

    public static void show(Context context, String postId, String commentId,
                            String reportedUserId, String reporterId) {
        boolean isComment = commentId != null;
        int padding = (int) (20 * context.getResources().getDisplayMetrics().density);

        LinearLayout form = new LinearLayout(context);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(padding, padding / 2, padding, 0);

        EditText reasonInput = new EditText(context);
        reasonInput.setHint("Reason (optional)");
        reasonInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        reasonInput.setMaxLines(4);
        reasonInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(200)});
        form.addView(reasonInput);

        new AlertDialog.Builder(context)
                .setTitle(isComment ? "Report comment" : "Report post")
                .setMessage("The organizers, moderators and admins will be notified and can review it.")
                .setView(form)
                .setPositiveButton("Report", (dialog, which) -> {
                    String reason = reasonInput.getText().toString().trim();
                    FirebaseRepository.getInstance(context).reportContent(postId, commentId,
                            reportedUserId, reporterId, reason, new FirebaseRepository.PostOperationCallback() {
                                @Override
                                public void onSuccess() {
                                    Toast.makeText(context, "Reported, thank you", Toast.LENGTH_SHORT).show();
                                }

                                @Override
                                public void onError(String errorMessage) {
                                    Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show();
                                }
                            });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
