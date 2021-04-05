package com.loohp.interactivechatdiscordsrvaddon.Listeners;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;

import com.cryptomorin.xseries.XMaterial;
import com.loohp.interactivechat.ConfigManager;
import com.loohp.interactivechat.InteractiveChat;
import com.loohp.interactivechat.API.InteractiveChatAPI;
import com.loohp.interactivechat.ObjectHolders.CustomPlaceholder;
import com.loohp.interactivechat.ObjectHolders.ICPlaceholder;
import com.loohp.interactivechat.ObjectHolders.ICPlayer;
import com.loohp.interactivechat.ObjectHolders.MentionPair;
import com.loohp.interactivechat.ObjectHolders.WebData;
import com.loohp.interactivechat.Utils.ChatColorUtils;
import com.loohp.interactivechat.Utils.CustomStringUtils;
import com.loohp.interactivechat.Utils.LanguageUtils;
import com.loohp.interactivechat.Utils.NBTUtils;
import com.loohp.interactivechat.Utils.PlaceholderParser;
import com.loohp.interactivechatdiscordsrvaddon.InteractiveChatDiscordSrvAddon;
import com.loohp.interactivechatdiscordsrvaddon.API.Events.DiscordImageEvent;
import com.loohp.interactivechatdiscordsrvaddon.API.Events.GameMessagePostProcessEvent;
import com.loohp.interactivechatdiscordsrvaddon.API.Events.GameMessagePreProcessEvent;
import com.loohp.interactivechatdiscordsrvaddon.API.Events.GameMessageProcessInventoryEvent;
import com.loohp.interactivechatdiscordsrvaddon.API.Events.GameMessageProcessItemEvent;
import com.loohp.interactivechatdiscordsrvaddon.API.Events.GameMessageProcessPlayerInventoryEvent;
import com.loohp.interactivechatdiscordsrvaddon.Graphics.ImageGeneration;
import com.loohp.interactivechatdiscordsrvaddon.ObjectHolders.DiscordDisplayData;
import com.loohp.interactivechatdiscordsrvaddon.ObjectHolders.DiscordMessageContent;
import com.loohp.interactivechatdiscordsrvaddon.ObjectHolders.HoverDisplayData;
import com.loohp.interactivechatdiscordsrvaddon.ObjectHolders.IDProvider;
import com.loohp.interactivechatdiscordsrvaddon.ObjectHolders.ImageDisplayData;
import com.loohp.interactivechatdiscordsrvaddon.ObjectHolders.ImageDisplayType;
import com.loohp.interactivechatdiscordsrvaddon.Registies.DiscordDataRegistry;
import com.loohp.interactivechatdiscordsrvaddon.Utils.ColorUtils;
import com.loohp.interactivechatdiscordsrvaddon.Utils.ComponentStringUtils;
import com.loohp.interactivechatdiscordsrvaddon.Utils.DiscordItemStackUtils;
import com.loohp.interactivechatdiscordsrvaddon.Utils.DiscordItemStackUtils.DiscordDescription;

import club.minnced.discord.webhook.WebhookClient;
import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.api.ListenerPriority;
import github.scarsz.discordsrv.api.Subscribe;
import github.scarsz.discordsrv.api.events.DiscordGuildMessagePostProcessEvent;
import github.scarsz.discordsrv.api.events.DiscordGuildMessageSentEvent;
import github.scarsz.discordsrv.api.events.GameChatMessagePreProcessEvent;
import github.scarsz.discordsrv.dependencies.jda.api.entities.ChannelType;
import github.scarsz.discordsrv.dependencies.jda.api.entities.Member;
import github.scarsz.discordsrv.dependencies.jda.api.entities.Message;
import github.scarsz.discordsrv.dependencies.jda.api.entities.TextChannel;
import github.scarsz.discordsrv.dependencies.jda.api.entities.User;
import github.scarsz.discordsrv.dependencies.jda.api.events.message.MessageReceivedEvent;
import github.scarsz.discordsrv.dependencies.jda.api.hooks.ListenerAdapter;
import github.scarsz.discordsrv.util.DiscordUtil;
import github.scarsz.discordsrv.util.PlaceholderUtil;
import github.scarsz.discordsrv.util.WebhookUtil;

public class OutboundToDiscordEvents {
	
	public static final Comparator<DiscordDisplayData> DISPLAY_DATA_COMPARATOR = Comparator.comparing(each -> each.getPosition());
	private static final IDProvider DATA_ID_PROVIDER = new IDProvider();
	public static final Map<Integer, DiscordDisplayData> DATA = Collections.synchronizedMap(new LinkedHashMap<>());
	
	@Subscribe(priority = ListenerPriority.LOW)
	public void onDiscordToGame(DiscordGuildMessagePostProcessEvent event) {
		InteractiveChatDiscordSrvAddon.plugin.messagesCounter.incrementAndGet();
		String message = event.getProcessedMessage();
		if (InteractiveChatDiscordSrvAddon.plugin.escapePlaceholdersFromDiscord) {
			for (String placeholder : InteractiveChat.aliasesMapping.keySet()) {
				message = message.replaceAll(placeholder, "\\" + placeholder);
			}
			for (ICPlaceholder placeholder : InteractiveChat.placeholderList) {
				message = message.replace(placeholder.getKeyword(), "\\" + placeholder.getKeyword());
			}
			event.setProcessedMessage(message);
		}
	}
	
	@SuppressWarnings("deprecation")
	@Subscribe(priority = ListenerPriority.HIGHEST)
	public void onGameToDiscord(GameChatMessagePreProcessEvent event) {
		InteractiveChatDiscordSrvAddon.plugin.messagesCounter.incrementAndGet();
		Player sender = event.getPlayer();
		ICPlayer wrappedSender = new ICPlayer(sender);
		String message = event.getMessage();
		String originalMessage = message;
		long now = System.currentTimeMillis();
		long uniCooldown = InteractiveChatAPI.getPlayerUniversalCooldown(sender) - now;
		
		if (!(uniCooldown < 0 || uniCooldown + 100 > InteractiveChat.universalCooldown)) {
			return;
		}

		GameMessagePreProcessEvent gameMessagePreProcessEvent = new GameMessagePreProcessEvent(sender, message, false);
		Bukkit.getPluginManager().callEvent(gameMessagePreProcessEvent);
		if (gameMessagePreProcessEvent.isCancelled()) {
			return;
		}
		message = gameMessagePreProcessEvent.getMessage();

		if (InteractiveChat.useItem && sender.hasPermission("interactivechat.module.item")) {
			long cooldown = InteractiveChatAPI.getPlayerPlaceholderCooldown(sender, InteractiveChat.itemPlaceholder) - now;
			if (cooldown < 0 || cooldown + 100 > ConfigManager.getConfig().getLong("ItemDisplay.Item.Cooldown") * 1000) {
				if (message.toLowerCase().contains(InteractiveChat.itemPlaceholder.toLowerCase())) {
					ItemStack item = sender.getEquipment().getItemInHand();
					if (item == null) {
						item = new ItemStack(Material.AIR);
					}
					XMaterial xMaterial = XMaterial.matchXMaterial(item);
					String itemStr;
					if (item.hasItemMeta() && item.getItemMeta().hasDisplayName() && !item.getItemMeta().getDisplayName().equals("")) {
						itemStr = item.getItemMeta().getDisplayName();
					} else {
						String itemKey = LanguageUtils.getTranslationKey(item);
						itemStr = LanguageUtils.getTranslation(itemKey, InteractiveChatDiscordSrvAddon.plugin.language);
						if (xMaterial.equals(XMaterial.PLAYER_HEAD)) {
							String owner = NBTUtils.getString(item, "SkullOwner", "Name");
							if (owner != null) {
								itemStr = itemStr.replaceFirst("%s", owner);
							}
						}
					}
					itemStr = ComponentStringUtils.stripColorAndConvertMagic(itemStr);
					
					int amount = item.getAmount();
					if (item == null || item.getType().equals(Material.AIR)) {
						amount = 1;
					}
				
					String replaceText = PlaceholderParser.parse(wrappedSender, ComponentStringUtils.stripColorAndConvertMagic(InteractiveChat.itemReplaceText).replace("{Amount}", String.valueOf(amount)).replace("{Item}", itemStr));
					message = message.replaceAll((InteractiveChat.itemCaseSensitive ? "" : "(?i)") + CustomStringUtils.escapeMetaCharacters(InteractiveChat.itemPlaceholder), replaceText);
					if (InteractiveChatDiscordSrvAddon.plugin.itemImage) {
						int inventoryId = DATA_ID_PROVIDER.getNext();
						int position = InteractiveChat.itemCaseSensitive ? originalMessage.indexOf(InteractiveChat.itemPlaceholder) : originalMessage.toLowerCase().indexOf(InteractiveChat.itemPlaceholder.toLowerCase());
						
						String title = PlaceholderParser.parse(wrappedSender, ComponentStringUtils.stripColorAndConvertMagic(InteractiveChat.itemTitle));
						
						Inventory inv = null;
						if (item.hasItemMeta() && item.getItemMeta() instanceof BlockStateMeta) {
							BlockState bsm = ((BlockStateMeta) item.getItemMeta()).getBlockState();
							if (bsm instanceof InventoryHolder) {
								Inventory container = ((InventoryHolder) bsm).getInventory();
								if (!container.isEmpty()) {
									inv = Bukkit.createInventory(null, container.getSize() + (container.getSize() % 9));
									for (int j = 0; j < container.getSize(); j++) {
										if (container.getItem(j) != null) {
											if (!container.getItem(j).getType().equals(Material.AIR)) {
												inv.setItem(j, container.getItem(j).clone());
											}
										}
									}
								}
							}
						}
						
						GameMessageProcessItemEvent gameMessageProcessItemEvent = new GameMessageProcessItemEvent(sender, title, message, false, inventoryId, item.clone(), inv);
						Bukkit.getPluginManager().callEvent(gameMessageProcessItemEvent);
						if (!gameMessageProcessItemEvent.isCancelled()) {
							message = gameMessageProcessItemEvent.getMessage();
							title = gameMessageProcessItemEvent.getTitle();
							if (gameMessageProcessItemEvent.hasInventory()) {
								DATA.put(inventoryId, new ImageDisplayData(sender, position, title, ImageDisplayType.ITEM_CONTAINER, gameMessageProcessItemEvent.getItemStack().clone(), gameMessageProcessItemEvent.getInventory()));
							} else {
								DATA.put(inventoryId, new ImageDisplayData(sender, position, title, ImageDisplayType.ITEM, gameMessageProcessItemEvent.getItemStack().clone()));
							}
						}
						message += "<ICD=" + inventoryId + ">";
					}
				}
			}
		}
		
		if (InteractiveChat.useInventory && sender.hasPermission("interactivechat.module.inventory")) {
			long cooldown = InteractiveChatAPI.getPlayerPlaceholderCooldown(sender, InteractiveChat.invPlaceholder) - now;
			if (cooldown < 0 || cooldown + 100 > ConfigManager.getConfig().getLong("ItemDisplay.Inventory.Cooldown") * 1000) {
				if (message.toLowerCase().contains(InteractiveChat.invPlaceholder.toLowerCase())) {
					String replaceText = PlaceholderParser.parse(wrappedSender, ComponentStringUtils.stripColorAndConvertMagic(InteractiveChat.invReplaceText));
					message = message.replaceAll((InteractiveChat.invCaseSensitive ? "" : "(?i)") + CustomStringUtils.escapeMetaCharacters(InteractiveChat.invPlaceholder), replaceText);
					if (InteractiveChatDiscordSrvAddon.plugin.invImage) {
						int inventoryId = DATA_ID_PROVIDER.getNext();
						int position = InteractiveChat.invCaseSensitive ? originalMessage.indexOf(InteractiveChat.invPlaceholder) : originalMessage.toLowerCase().indexOf(InteractiveChat.invPlaceholder.toLowerCase());
						
						Inventory inv = Bukkit.createInventory(null, 45);
						for (int j = 0; j < sender.getInventory().getSize(); j++) {
							if (sender.getInventory().getItem(j) != null) {
								if (!sender.getInventory().getItem(j).getType().equals(Material.AIR)) {
									inv.setItem(j, sender.getInventory().getItem(j).clone());
								}
							}
						}
						String title = PlaceholderParser.parse(wrappedSender, ComponentStringUtils.stripColorAndConvertMagic(InteractiveChat.invTitle));
						
						GameMessageProcessPlayerInventoryEvent gameMessageProcessPlayerInventoryEvent = new GameMessageProcessPlayerInventoryEvent(sender, title, message, false, inventoryId, inv);
						Bukkit.getPluginManager().callEvent(gameMessageProcessPlayerInventoryEvent);
						if (!gameMessageProcessPlayerInventoryEvent.isCancelled()) {
							message = gameMessageProcessPlayerInventoryEvent.getMessage();
							title = gameMessageProcessPlayerInventoryEvent.getTitle();
							DATA.put(inventoryId, new ImageDisplayData(sender, position, title, ImageDisplayType.INVENTORY, true, gameMessageProcessPlayerInventoryEvent.getInventory()));
						}
						
						message += "<ICD=" + inventoryId + ">";
					}
				}
			}
		}
		
		if (InteractiveChat.useEnder && sender.hasPermission("interactivechat.module.enderchest")) {
			long cooldown = InteractiveChatAPI.getPlayerPlaceholderCooldown(sender, InteractiveChat.enderPlaceholder) - now;
			if (cooldown < 0 || cooldown + 100 > ConfigManager.getConfig().getLong("ItemDisplay.EnderChest.Cooldown") * 1000) {
				if (message.toLowerCase().contains(InteractiveChat.enderPlaceholder.toLowerCase())) {
					String replaceText = PlaceholderParser.parse(wrappedSender, ComponentStringUtils.stripColorAndConvertMagic(InteractiveChat.enderReplaceText));
					message = message.replaceAll((InteractiveChat.enderCaseSensitive ? "" : "(?i)") + CustomStringUtils.escapeMetaCharacters(InteractiveChat.enderPlaceholder), replaceText);
					if (InteractiveChatDiscordSrvAddon.plugin.enderImage) {
						int inventoryId = DATA_ID_PROVIDER.getNext();
						int position = InteractiveChat.enderCaseSensitive ? originalMessage.indexOf(InteractiveChat.enderPlaceholder) : originalMessage.toLowerCase().indexOf(InteractiveChat.enderPlaceholder.toLowerCase());
						
						Inventory inv = Bukkit.createInventory(null, 27);
						for (int j = 0; j < sender.getEnderChest().getSize(); j++) {
							if (sender.getEnderChest().getItem(j) != null) {
								if (!sender.getEnderChest().getItem(j).getType().equals(Material.AIR)) {
									inv.setItem(j, sender.getEnderChest().getItem(j).clone());
								}
							}
						}
						String title = PlaceholderParser.parse(wrappedSender, ComponentStringUtils.stripColorAndConvertMagic(InteractiveChat.enderTitle));
						
						GameMessageProcessInventoryEvent gameMessageProcessInventoryEvent = new GameMessageProcessInventoryEvent(sender, title, message, false, inventoryId, inv);
						Bukkit.getPluginManager().callEvent(gameMessageProcessInventoryEvent);
						if (!gameMessageProcessInventoryEvent.isCancelled()) {
							message = gameMessageProcessInventoryEvent.getMessage();
							title = gameMessageProcessInventoryEvent.getTitle();
							DATA.put(inventoryId, new ImageDisplayData(sender, position, title, ImageDisplayType.ENDERCHEST, gameMessageProcessInventoryEvent.getInventory()));
						}
						
						message += "<ICD=" + inventoryId + ">";
					}
				}
			}
		}
		
		for (ICPlaceholder placeholder : InteractiveChatAPI.getICPlaceholderList()) {
			if (!placeholder.isBuildIn()) {
				CustomPlaceholder customP = placeholder.getCustomPlaceholder().get();
				if (!InteractiveChat.useCustomPlaceholderPermissions || (InteractiveChat.useCustomPlaceholderPermissions && sender.hasPermission(customP.getPermission()))) {
					long cooldown = InteractiveChatAPI.getPlayerPlaceholderCooldown(sender, customP.getKeyword()) - now;
					if (message.toLowerCase().contains(customP.getKeyword().toLowerCase()) && (cooldown < 0 || cooldown + 100 > customP.getCooldown())) {
						String replaceText = customP.getKeyword();
						if (customP.getReplace().isEnabled()) {
							replaceText = PlaceholderParser.parse(wrappedSender, ComponentStringUtils.stripColorAndConvertMagic(customP.getReplace().getReplaceText()));
							message = message.replaceAll((customP.isCaseSensitive() ? "" : "(?i)") + CustomStringUtils.escapeMetaCharacters(customP.getKeyword()), replaceText);
						}
						if (InteractiveChatDiscordSrvAddon.plugin.hoverEnabled && customP.getHover().isEnabled() && !InteractiveChatDiscordSrvAddon.plugin.hoverIngore.contains(customP.getPosition())) {
							int hoverId = DATA_ID_PROVIDER.getNext();
							int position = customP.isCaseSensitive() ? originalMessage.indexOf(customP.getKeyword()) : originalMessage.toLowerCase().indexOf(customP.getKeyword().toLowerCase());
							
							String hoverText = PlaceholderParser.parse(wrappedSender, ComponentStringUtils.stripColorAndConvertMagic(customP.getHover().getText()));
							Color color = ColorUtils.getFirstColor(customP.getHover().getText());
							if (color == null) {
								color = DiscordDataRegistry.DISCORD_HOVER_COLOR;
							}
							DATA.put(hoverId, new HoverDisplayData(sender, position, replaceText, hoverText, color));
							message += "<ICD=" + hoverId + ">";
						}
					}
				}
			}
		}
		
		if (InteractiveChat.t && WebData.getInstance() != null) {
			for (CustomPlaceholder customP : WebData.getInstance().getSpecialPlaceholders()) {
				long cooldown = InteractiveChatAPI.getPlayerPlaceholderCooldown(sender, customP.getKeyword()) - now;
				if (message.toLowerCase().contains(customP.getKeyword().toLowerCase()) && (cooldown < 0 || cooldown + 100 > customP.getCooldown())) {
					String replaceText = customP.getKeyword();
					if (customP.getReplace().isEnabled()) {
						replaceText = PlaceholderParser.parse(wrappedSender, ComponentStringUtils.stripColorAndConvertMagic(customP.getReplace().getReplaceText()));
						message = message.replaceAll((customP.isCaseSensitive() ? "" : "(?i)") + CustomStringUtils.escapeMetaCharacters(customP.getKeyword()), replaceText);
					}
					if (InteractiveChatDiscordSrvAddon.plugin.hoverEnabled && customP.getHover().isEnabled() && !InteractiveChatDiscordSrvAddon.plugin.hoverIngore.contains(customP.getPosition())) {
						int hoverId = DATA_ID_PROVIDER.getNext();
						int position = customP.isCaseSensitive() ? originalMessage.indexOf(customP.getKeyword()) : originalMessage.toLowerCase().indexOf(customP.getKeyword().toLowerCase());
						
						String hoverText = PlaceholderParser.parse(wrappedSender, ComponentStringUtils.stripColorAndConvertMagic(customP.getHover().getText()));
						Color color = ColorUtils.getFirstColor(customP.getHover().getText());
						if (color == null) {
							color = DiscordDataRegistry.DISCORD_HOVER_COLOR;
						}
						DATA.put(hoverId, new HoverDisplayData(sender, position, replaceText, hoverText, color));
						message += "<ICD=" + hoverId + ">";
					}
				}
			}
		}
		
		DiscordSRV srv = InteractiveChatDiscordSrvAddon.discordsrv;
		if (InteractiveChatDiscordSrvAddon.plugin.translateMentions) {
			for (MentionPair pair : InteractiveChat.mentionPair.values()) {
				if (pair.getSender().equals(sender.getUniqueId())) {
					UUID recieverUUID = pair.getReciever();
					Set<String> names = new HashSet<>();
					Player reciever = Bukkit.getPlayer(recieverUUID);
					if (reciever != null) {
						names.add(ChatColorUtils.stripColor(reciever.getName()));
						if (!names.contains(ChatColorUtils.stripColor(reciever.getDisplayName()))) {
							names.add(ChatColorUtils.stripColor(reciever.getDisplayName()));
						}
						List<String> list = InteractiveChatAPI.getNicknames(reciever.getUniqueId());
						for (String name : list) {
							names.add(ChatColorUtils.stripColor(name));
						}
					} else {
						ICPlayer icplayer = InteractiveChat.remotePlayers.get(recieverUUID);
						if (icplayer != null) {
							names.add(icplayer.getDisplayName());
						}
					}
					String userId = srv.getAccountLinkManager().getDiscordId(recieverUUID);
					if (userId != null) {
						User user = srv.getJda().getUserById(userId);
						if (user != null) {
							String discordMention = user.getAsMention();
							for (String name : names) {
								if (message.contains(InteractiveChat.mentionPrefix + name)) {
									message = message.replace(InteractiveChat.mentionPrefix + name, discordMention);
								}
							}
						}
					}
				}
			}
		}
		
		GameMessagePostProcessEvent gameMessagePostProcessEvent = new GameMessagePostProcessEvent(sender, message, false);
		Bukkit.getPluginManager().callEvent(gameMessagePostProcessEvent);
		if (gameMessagePostProcessEvent.isCancelled()) {
			return;
		}
		message = gameMessagePostProcessEvent.getMessage();
		
		event.setMessage(message);
	}
	
	@Subscribe(priority = ListenerPriority.HIGHEST)
	public void discordMessageSent(DiscordGuildMessageSentEvent event) {
		Message message = event.getMessage();
		String textOriginal = message.getContentRaw();
		TextChannel channel = event.getChannel();
		
		if (!InteractiveChatDiscordSrvAddon.plugin.isEnabled()) {
			return;
		}
		Bukkit.getScheduler().runTaskAsynchronously(InteractiveChatDiscordSrvAddon.plugin, () -> {
			String text = textOriginal;
			
			if (!text.contains("<ICD=")) {
				return;
			}
			
			Set<Integer> matches = new LinkedHashSet<>();
			
			for (int key : DATA.keySet()) {
				if (text.contains("<ICD=" + key + ">")) {
					text = text.replace("<ICD=" + key + ">", "");
					matches.add(key);
				}
			}
			
			if (matches.isEmpty()) {
				return;
			}
			
			message.delete().queue();
			
			List<DiscordMessageContent> contents = new ArrayList<>();
			List<DiscordDisplayData> dataList = new ArrayList<>();
			
			for (int key : matches) {
				DiscordDisplayData data = DATA.remove(key); 
				if (data != null) {
					dataList.add(data);
				}
			}
			
			Collections.sort(dataList, DISPLAY_DATA_COMPARATOR);
			
			for (DiscordDisplayData data : dataList) {
				if (data instanceof ImageDisplayData) {
					ImageDisplayData iData = (ImageDisplayData) data;
					ImageDisplayType type = iData.getType();
					String title = iData.getTitle();
					if (iData.getItemStack().isPresent()) {
						ItemStack item = iData.getItemStack().get();
						Color color = DiscordItemStackUtils.getDiscordColor(item);
						if (color == null || color.equals(Color.white)) {
							color = new Color(0xFFFFFE);
						}
						try {
							if (type.equals(ImageDisplayType.ITEM_CONTAINER)) {
								DiscordDescription description = DiscordItemStackUtils.getDiscordDescription(item);
								BufferedImage image = ImageGeneration.getItemStackImage(item);
								ByteArrayOutputStream itemOs = new ByteArrayOutputStream();
								ImageIO.write(image, "png", itemOs);
								BufferedImage container = ImageGeneration.getInventoryImage(iData.getInventory().get());
								ByteArrayOutputStream contentOs = new ByteArrayOutputStream();
								ImageIO.write(container, "png", contentOs);
								DiscordMessageContent content = new DiscordMessageContent(description.getName(), "attachment://Item.png", description.getDescription().orElse(null), "attachment://Container.png", color);
								content.addAttachment("Item.png", itemOs.toByteArray());
								content.addAttachment("Container.png", contentOs.toByteArray());
								contents.add(content);
							} else {
								DiscordDescription description = DiscordItemStackUtils.getDiscordDescription(item);
								BufferedImage image = ImageGeneration.getItemStackImage(item);
								ByteArrayOutputStream itemOs = new ByteArrayOutputStream();
								ImageIO.write(image, "png", itemOs);
								if (iData.isFilledMap()) {
									BufferedImage map = ImageGeneration.getMapImage(item);
									ByteArrayOutputStream mapOs = new ByteArrayOutputStream();
									ImageIO.write(map, "png", mapOs);
									DiscordMessageContent content = new DiscordMessageContent(description.getName(), "attachment://Item.png", description.getDescription().orElse(null), "attachment://Map.png", color);
									content.addAttachment("Item.png", itemOs.toByteArray());
									content.addAttachment("Map.png", mapOs.toByteArray());
									contents.add(content);
								} else {
									DiscordMessageContent content = new DiscordMessageContent(description.getName(), "attachment://Item.png", description.getDescription().orElse(null), null, color);
									content.addAttachment("Item.png", itemOs.toByteArray());
									contents.add(content);
								}
							}
						} catch (Exception e) {
							e.printStackTrace();
						}	
					} else if (iData.getInventory().isPresent()) {
						Inventory inv = iData.getInventory().get();
						try {
							BufferedImage image;
							if (iData.isPlayerInventory()) {
								if (InteractiveChatDiscordSrvAddon.plugin.usePlayerInvView) {
									image = ImageGeneration.getPlayerInventoryImage(inv, iData.getPlayer());
								} else {
									image = ImageGeneration.getInventoryImage(inv);
								}
							} else {
								image = ImageGeneration.getInventoryImage(inv);
							}
							ByteArrayOutputStream os = new ByteArrayOutputStream();
							Color color;
							switch (type) {
							case ENDERCHEST:
								color = InteractiveChatDiscordSrvAddon.plugin.enderColor;
								break;
							case INVENTORY:
								color = InteractiveChatDiscordSrvAddon.plugin.invColor;
								break;
							default:
								color = Color.black;
								break;
							}
							ImageIO.write(image, "png", os);
							DiscordMessageContent content = new DiscordMessageContent(title, null, null, "attachment://Inventory.png", color);
							content.addAttachment("Inventory.png", os.toByteArray());
							contents.add(content);
						} catch (Exception e) {
							e.printStackTrace();
						}			
					}
				} else if (data instanceof HoverDisplayData) {
					try {
						HoverDisplayData hData = (HoverDisplayData) data;
						String title = hData.getDisplayText();
						String body = hData.getHoverText();
						Color color = hData.getColor();
						DiscordMessageContent content = new DiscordMessageContent(title, null, body, null, color);
						if (InteractiveChatDiscordSrvAddon.plugin.hoverImage) {
							BufferedImage image = InteractiveChatDiscordSrvAddon.plugin.getMiscTexture("hover_cursor");
							ByteArrayOutputStream os = new ByteArrayOutputStream();
							ImageIO.write(image, "png", os);
							content.setAuthorIconUrl("attachment://Hover.png");
							content.addAttachment("Hover.png", os.toByteArray());
						}
						contents.add(content);
					} catch (Exception e) {
						e.printStackTrace();
					}	
				}
			}
			
			DiscordImageEvent discordImageEvent = new DiscordImageEvent(channel, textOriginal, text, contents, false, true);
			TextChannel textChannel = discordImageEvent.getChannel();
			if (discordImageEvent.isCancelled()) {
				String restore = discordImageEvent.getOriginalMessage();
				textChannel.sendMessage(restore).queue();
			} else {
				text = discordImageEvent.getNewMessage();
				textChannel.sendMessage(text).queue();
				for (DiscordMessageContent content : discordImageEvent.getDiscordMessageContents()) {
					content.toJDAMessageAction(textChannel).queue();
				}
			}
		});
	}
	
	public static class JDAEvents extends ListenerAdapter {
		
		@Override
		public void onMessageReceived(MessageReceivedEvent event) {
			if (event.getAuthor().equals(event.getJDA().getSelfUser())) {
				return;
			}
			if (!event.getChannelType().equals(ChannelType.TEXT)) {
				return;
			}
			if (!event.isWebhookMessage()) {
				return;
			}
			Message message = event.getMessage();
			TextChannel channel = event.getTextChannel();
			String textOriginal = message.getContentRaw();
			String text = textOriginal;
			if (!text.contains("<ICD=")) {
				return;
			}
			
			Set<Integer> matches = new LinkedHashSet<>();
			
			for (int key : OutboundToDiscordEvents.DATA.keySet()) {
				if (text.contains("<ICD=" + key + ">")) {
					text = text.replace("<ICD=" + key + ">", "");
					matches.add(key);
				}
			}
			if (matches.isEmpty()) {
				return;
			}
			
			message.delete().queue();
			Player player = OutboundToDiscordEvents.DATA.get(matches.iterator().next()).getPlayer();

			List<DiscordMessageContent> contents = new ArrayList<>();
			List<DiscordDisplayData> dataList = new ArrayList<>();
			
			for (int key : matches) {
				DiscordDisplayData data = DATA.remove(key); 
				if (data != null) {
					dataList.add(data);
				}
			}
			
			Collections.sort(dataList, DISPLAY_DATA_COMPARATOR);
			
			for (DiscordDisplayData data : dataList) {
				if (data instanceof ImageDisplayData) {
					ImageDisplayData iData = (ImageDisplayData) data;
					ImageDisplayType type = iData.getType();
					String title = iData.getTitle();
					if (iData.getItemStack().isPresent()) {
						ItemStack item = iData.getItemStack().get();
						Color color = DiscordItemStackUtils.getDiscordColor(item);
						if (color == null || color.equals(Color.white)) {
							color = new Color(0xFFFFFE);
						}
						try {
							if (type.equals(ImageDisplayType.ITEM_CONTAINER)) {
								DiscordDescription description = DiscordItemStackUtils.getDiscordDescription(item);
								BufferedImage image = ImageGeneration.getItemStackImage(item);
								ByteArrayOutputStream itemOs = new ByteArrayOutputStream();
								ImageIO.write(image, "png", itemOs);
								BufferedImage container = ImageGeneration.getInventoryImage(iData.getInventory().get());
								ByteArrayOutputStream contentOs = new ByteArrayOutputStream();
								ImageIO.write(container, "png", contentOs);
								DiscordMessageContent content = new DiscordMessageContent(description.getName(), "attachment://Item.png", description.getDescription().orElse(null), "attachment://Container.png", color);
								content.addAttachment("Item.png", itemOs.toByteArray());
								content.addAttachment("Container.png", contentOs.toByteArray());
								contents.add(content);
							} else {
								DiscordDescription description = DiscordItemStackUtils.getDiscordDescription(item);
								BufferedImage image = ImageGeneration.getItemStackImage(item);
								ByteArrayOutputStream itemOs = new ByteArrayOutputStream();
								ImageIO.write(image, "png", itemOs);
								if (iData.isFilledMap()) {
									BufferedImage map = ImageGeneration.getMapImage(item);
									ByteArrayOutputStream mapOs = new ByteArrayOutputStream();
									ImageIO.write(map, "png", mapOs);
									DiscordMessageContent content = new DiscordMessageContent(description.getName(), "attachment://Item.png", description.getDescription().orElse(null), "attachment://Map.png", color);
									content.addAttachment("Item.png", itemOs.toByteArray());
									content.addAttachment("Map.png", mapOs.toByteArray());
									contents.add(content);
								} else {
									DiscordMessageContent content = new DiscordMessageContent(description.getName(), "attachment://Item.png", description.getDescription().orElse(null), null, color);
									content.addAttachment("Item.png", itemOs.toByteArray());
									contents.add(content);
								}
							}
						} catch (Exception e) {
							e.printStackTrace();
						}	
					} else if (iData.getInventory().isPresent()) {
						Inventory inv = iData.getInventory().get();
						try {
							BufferedImage image;
							if (iData.isPlayerInventory()) {
								if (InteractiveChatDiscordSrvAddon.plugin.usePlayerInvView) {
									image = ImageGeneration.getPlayerInventoryImage(inv, iData.getPlayer());
								} else {
									image = ImageGeneration.getInventoryImage(inv);
								}
							} else {
								image = ImageGeneration.getInventoryImage(inv);
							}
							ByteArrayOutputStream os = new ByteArrayOutputStream();
							Color color;
							switch (type) {
							case ENDERCHEST:
								color = InteractiveChatDiscordSrvAddon.plugin.enderColor;
								break;
							case INVENTORY:
								color = InteractiveChatDiscordSrvAddon.plugin.invColor;
								break;
							default:
								color = Color.black;
								break;
							}
							ImageIO.write(image, "png", os);
							DiscordMessageContent content = new DiscordMessageContent(title, null, null, "attachment://Inventory.png", color);
							content.addAttachment("Inventory.png", os.toByteArray());
							contents.add(content);
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				} else if (data instanceof HoverDisplayData) {
					try {
						HoverDisplayData hData = (HoverDisplayData) data;
						String title = hData.getDisplayText();
						String body = hData.getHoverText();
						Color color = hData.getColor();
						DiscordMessageContent content = new DiscordMessageContent(title, null, body, null, color);
						if (InteractiveChatDiscordSrvAddon.plugin.hoverImage) {
							BufferedImage image = InteractiveChatDiscordSrvAddon.plugin.getMiscTexture("hover_cursor");
							ByteArrayOutputStream os = new ByteArrayOutputStream();
							ImageIO.write(image, "png", os);
							content.setAuthorIconUrl("attachment://Hover.png");
							content.addAttachment("Hover.png", os.toByteArray());
						}
						contents.add(content);
					} catch (Exception e) {
						e.printStackTrace();
					}	
				}
			}
			List<WebhookMessageBuilder> messagesToSend = new ArrayList<>();
			
			DiscordImageEvent discordImageEvent = new DiscordImageEvent(channel, textOriginal, text, contents, false, true);
			TextChannel textChannel = discordImageEvent.getChannel();
			if (discordImageEvent.isCancelled()) {
				String restore = discordImageEvent.getOriginalMessage();
				messagesToSend.add(new WebhookMessageBuilder().setContent(restore));
			} else {
				text = discordImageEvent.getNewMessage();
				messagesToSend.add(new WebhookMessageBuilder().setContent(text));
				for (DiscordMessageContent content : discordImageEvent.getDiscordMessageContents()) {
					messagesToSend.add(content.toWebhookMessageBuilder());
				}
			}
			
			String avatarUrl = DiscordSRV.getAvatarUrl((Player) player);
            String username = DiscordSRV.config().getString("Experiment_WebhookChatMessageUsernameFormat")
                    .replace("%displayname%", DiscordUtil.strip(player.getDisplayName()))
                    .replace("%username%", player.getName());
            username = PlaceholderUtil.replacePlaceholders(username, player);
            username = DiscordUtil.strip(username);

            String userId = DiscordSRV.getPlugin().getAccountLinkManager().getDiscordId(player.getUniqueId());
            if (userId != null) {
                Member member = DiscordUtil.getMemberById(userId);
                if (member != null) {
                    if (DiscordSRV.config().getBoolean("Experiment_WebhookChatMessageAvatarFromDiscord"))
                        avatarUrl = member.getUser().getEffectiveAvatarUrl();
                    if (DiscordSRV.config().getBoolean("Experiment_WebhookChatMessageUsernameFromDiscord"))
                        username = member.getEffectiveName();
                }
            }
			
			String webHookUrl = WebhookUtil.getWebhookUrlToUseForChannel(textChannel, username);
			WebhookClient client = WebhookClient.withUrl(webHookUrl);
			
			if (client == null) {
				throw new NullPointerException("Unable to get the Webhook client URL for the TextChannel " + textChannel.getName());
			}
			
			for (WebhookMessageBuilder builder : messagesToSend) {
				client.send(builder.setUsername(username).setAvatarUrl(avatarUrl).build());
			}
			client.close();
		}
	}

}