package com.suici.roverhood;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import android.os.Handler;
import android.os.Looper;

import com.suici.roverhood.databinding.LoadingBinding;
import com.suici.roverhood.databinding.LogInBinding;

import java.util.Objects;

public class Loading extends Fragment {

    private LoadingBinding binding;

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {
        binding = LoadingBinding.inflate(inflater, container, false);

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (NavHostFragment.findNavController(Loading.this)
                    .getCurrentDestination().getId() == R.id.loading) {
                NavHostFragment.findNavController(Loading.this)
                        .navigate(R.id.action_loading_to_RoverFeed);
            }
        }, 1000); // 1 second
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}