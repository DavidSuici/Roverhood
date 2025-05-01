package com.suici.roverhood;

import android.graphics.Typeface;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import java.util.Objects;

public class Post {
    Fragment activeFragment;
    String date;
    String user;
    String id;
    String description;
    int picture;

    int dpValue = 0;
    LinearLayout.LayoutParams params;
    LinearLayout.LayoutParams dividerParams;
    LinearLayout.LayoutParams userParams;
    LinearLayout.LayoutParams dateParams;

    public Post(Fragment fragment, String date, String user, String description, int picture) {
        this.activeFragment = fragment;
        this.date = date;
        this.user = user;
        this.id = "1";
        this.description = description;
        this.picture = picture;

        initializeParams();
    }

    private void initializeParams () {
        float density = activeFragment.getActivity().getResources().getDisplayMetrics().density;
        dpValue = (int) (density);

        params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(10*dpValue, 10*dpValue, 10*dpValue, 10*dpValue);

        dividerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (int) ( 4 * density)
        );
        dividerParams.setMargins(10*dpValue, 10*dpValue, 10*dpValue, 10*dpValue);

        dateParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        dateParams.setMargins(10*dpValue, 0*dpValue, 10*dpValue, 0);

        userParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        userParams.setMargins(10*dpValue, 10*dpValue, 10*dpValue, 0);
    }

    public View getUserView() {
        TextView userView = new TextView(activeFragment.getActivity());
        userView.setText(this.user);
        userView.setLayoutParams(userParams);
        userView.setTypeface(userView.getTypeface(), Typeface.BOLD);
        return userView;
    }

    public View getDateView() {
        TextView dateView = new TextView(activeFragment.getActivity());
        dateView.setText(this.date);
        dateView.setLayoutParams(dateParams);
        dateView.setTypeface(dateView.getTypeface(), Typeface.ITALIC);
        return dateView;
    }

    public View getDescriptionView() {
        TextView descriptionView = new TextView(activeFragment.getActivity());
        descriptionView.setText(this.description);
        descriptionView.setLayoutParams(params);
        return descriptionView;
    }

    public View getImageView() {
        ImageView imageView = new ImageView(activeFragment.getActivity());
        imageView.setImageResource(picture);
        imageView.setLayoutParams(params);
        imageView.setAdjustViewBounds(true);
        return imageView;
    }

    public View getDividerView() {
        androidx.cardview.widget.CardView dividerView = new androidx.cardview.widget.CardView(activeFragment.getActivity());
        dividerView.setLayoutParams(dividerParams);
        dividerView.setCardBackgroundColor(activeFragment.getResources().getColor(R.color.light_purple));
        dividerView.setRadius( 5 * dpValue);
        return dividerView;
    }

    public View getEndView() {
        View endView = new View(activeFragment.getActivity());
        endView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                100*dpValue)
        );
        //endView.setBackground(activeFragment.getResources().getDrawable(R.drawable.bg));
        return endView;
    }

    public boolean isAnnouncement() {
        return Objects.equals(user, "Admin");
    }
}
