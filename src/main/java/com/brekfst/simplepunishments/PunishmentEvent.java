package com.brekfst.simplepunishments;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Cancellable;

public class PunishmentEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Punishment punishment;
    private boolean cancelled;

    public PunishmentEvent(Punishment punishment) {
        this.punishment = punishment;
        this.cancelled = false;
    }

    public Punishment getPunishment() {
        return punishment;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}