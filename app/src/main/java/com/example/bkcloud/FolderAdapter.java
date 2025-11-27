package com.example.bkcloud;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class FolderAdapter extends RecyclerView.Adapter<FolderAdapter.ViewHolder> {

    public interface OnFolderClick {
        void onClick(String folderName);
    }

    List<String> folders;
    OnFolderClick listener;

    public FolderAdapter(List<String> folders, OnFolderClick listener) {
        this.folders = folders;
        this.listener = listener;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView txtFolderName;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            txtFolderName = itemView.findViewById(R.id.txtFolderName);
        }
    }

    @NonNull
    @Override
    public FolderAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_folder, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull FolderAdapter.ViewHolder holder, int position) {
        String folderName = folders.get(position);
        holder.txtFolderName.setText(folderName);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onClick(folderName);
            }
        });
    }

    @Override
    public int getItemCount() {
        return folders.size();
    }
}
