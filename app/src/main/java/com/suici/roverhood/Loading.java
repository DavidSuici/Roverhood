package com.suici.roverhood;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.suici.roverhood.databinding.LoadingBinding;
import com.suici.roverhood.databinding.LogInBinding;

public class Loading extends Fragment {

    private LoadingBinding binding;

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {
        binding = LoadingBinding.inflate(inflater, container, false);

        {
            NavHostFragment.findNavController(Loading.this)
                    .navigate(R.id.action_loading_to_RoverFeed);
        }

        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}