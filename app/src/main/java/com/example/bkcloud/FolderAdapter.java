package com.example.bkcloud;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class FolderAdapter extends RecyclerView.Adapter<FolderAdapter.ViewHolder> {

    public interface OnFolderClick {
        void onClick(String folderName);
    }

    List<FolderItem> folders;
    List<FolderItem> originalList;
    OnFolderClick listener;
    String selectedFolderName = null;

    public FolderAdapter(List<FolderItem> folders, OnFolderClick listener) {
        this.folders = folders;
        this.originalList = new ArrayList<>(folders);
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
        FolderItem item = folders.get(position);

        holder.txtFolderName.setText(
                item.name + "  (" + formatSize(item.size) + ")"
        );

        holder.itemView.setOnClickListener(v -> {
            if (item.name.equals(selectedFolderName)) {
                selectedFolderName = null;
                listener.onClick(null);
            } else {
                selectedFolderName = item.name;
                listener.onClick(item.name);
            }
            notifyDataSetChanged();
        });

        if (item.name.equals(selectedFolderName)) {
            holder.itemView.setBackgroundResource(R.drawable.folder_selected_bg);
        } else {
            holder.itemView.setBackgroundResource(android.R.color.transparent);
        }
    }

    @Override
    public int getItemCount() {
        return folders.size();
    }

    public static class FolderItem {
        public String name;
        public long size;

        public FolderItem(String name, long size) {
            this.name = name;
            this.size = size;
        }
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format("%.1f KB", kb);
        double mb = kb / 1024;
        if (mb < 1024) return String.format("%.1f MB", mb);
        double gb = mb / 1024;
        return String.format("%.1f GB", gb);
    }

    public void filterByFolderSet(List<String> allowedFolders) {
        folders.clear();

        for (FolderItem f : originalList) {
            if (allowedFolders.contains(f.name)) {
                folders.add(f);
            }
        }

        notifyDataSetChanged();
    }

    public void resetAll() {
        folders.clear();
        folders.addAll(originalList);
        notifyDataSetChanged();
    }

    public void setSelectedFolder(String name) {
        selectedFolderName = name;
        notifyDataSetChanged();
    }

}
