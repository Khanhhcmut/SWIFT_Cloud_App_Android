package com.example.bkcloud;

import android.graphics.Color;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FolderAdapter extends RecyclerView.Adapter<FolderAdapter.ViewHolder> {

    public interface OnFolderClick {
        void onClick(String folderName);
    }

    List<FolderItem> folders;
    List<FolderItem> originalList;
    OnFolderClick listener;
    String selectedFolderName = null;

    boolean deleteMode = false;
    Set<String> selectedSet = new HashSet<>();
    FolderListener deleteListener;

    public FolderAdapter(List<FolderItem> folders, OnFolderClick listener) {
        this.folders = folders;
        this.originalList = new ArrayList<>(folders);
        this.listener = listener;
    }

    public interface FolderListener {
        void onLongPress(String name);
        void onToggleSelect(String name);
        void onDeleteIcon();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView txtFolderName;
        ImageView deleteIcon;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            txtFolderName = itemView.findViewById(R.id.txtFolderName);
            deleteIcon = itemView.findViewById(R.id.deleteIcon);
        }
    }

    public void setDeleteMode(boolean mode, Set<String> set) {
        deleteMode = mode;
        selectedSet = new HashSet<>(set);
        notifyDataSetChanged();
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
        String key = item.name;

        holder.txtFolderName.setText(
                item.name + "  (" + formatSize(item.size) + ")"
        );
        holder.itemView.setOnTouchListener(new View.OnTouchListener() {
            Handler handler = new Handler();
            boolean isLong = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    isLong = false;
                    handler.postDelayed(() -> {
                        isLong = true;
                        if (deleteListener != null) deleteListener.onLongPress(key);
                    }, 1500);
                }

                if (event.getAction() == MotionEvent.ACTION_UP ||
                        event.getAction() == MotionEvent.ACTION_CANCEL) {

                    handler.removeCallbacksAndMessages(null);

                    if (deleteMode && !isLong) {
                        if (deleteListener != null) deleteListener.onToggleSelect(key);
                    }
                }

                return false;
            }
        });

        holder.itemView.setOnClickListener(v -> {
            if (deleteMode) {
                if (deleteListener != null) deleteListener.onToggleSelect(key);
                return;
            }

            // logic cũ khi không ở delete mode
            if (item.name.equals(selectedFolderName)) {
                selectedFolderName = null;
                listener.onClick(null);
            } else {
                selectedFolderName = item.name;
                listener.onClick(item.name);
            }
            notifyDataSetChanged();
        });

        holder.deleteIcon.setOnClickListener(v -> {
            if (deleteListener != null) deleteListener.onDeleteIcon();
        });

        if (deleteMode) {
            holder.deleteIcon.setVisibility(View.VISIBLE);

            if (selectedSet.contains(item.name)) {
                holder.itemView.setBackgroundColor(Color.parseColor("#33FF0000"));
            } else {
                holder.itemView.setBackgroundColor(Color.TRANSPARENT);
            }

        } else {
            holder.deleteIcon.setVisibility(View.GONE);

            if (item.name.equals(selectedFolderName)) {
                holder.itemView.setBackgroundResource(R.drawable.folder_selected_bg);
            } else {
                holder.itemView.setBackgroundResource(android.R.color.transparent);
            }
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
