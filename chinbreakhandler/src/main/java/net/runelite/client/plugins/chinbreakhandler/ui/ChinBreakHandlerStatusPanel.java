package net.runelite.client.plugins.chinbreakhandler.ui;

import net.runelite.client.plugins.chinbreakhandler.ChinBreakHandler;
import net.runelite.client.plugins.chinbreakhandler.ChinBreakHandlerPlugin;
import static net.runelite.client.plugins.chinbreakhandler.ChinBreakHandlerPlugin.CONFIG_GROUP;
import static net.runelite.client.plugins.chinbreakhandler.ChinBreakHandlerPlugin.sanitizedName;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.ui.PluginPanel;

@Slf4j
public class ChinBreakHandlerStatusPanel extends JPanel
{
	private final ConfigManager configManager;
	private final ChinBreakHandlerPlugin chinBreakHandlerPlugin;
	private final ChinBreakHandler chinBreakHandler;
	private final Plugin plugin;

	private final JPanel contentPanel = new JPanel(new GridBagLayout());
	private final JPanel infoPanel = new JPanel(new GridBagLayout());
	private final JLabel timeLabel = new JLabel();
	private final JLabel runtimeLabel = new JLabel();
	private final JLabel breaksLabel = new JLabel();

	@Override
	public Dimension getPreferredSize()
	{
		return new Dimension(PluginPanel.PANEL_WIDTH, super.getPreferredSize().height);
	}

	@SuppressWarnings("ResultOfMethodCallIgnored")
	ChinBreakHandlerStatusPanel(ChinBreakHandlerPlugin chinBreakHandlerPlugin, ChinBreakHandler chinBreakHandler, Plugin plugin)
	{
		this.configManager = chinBreakHandlerPlugin.getConfigManager();
		this.chinBreakHandlerPlugin = chinBreakHandlerPlugin;
		this.chinBreakHandler = chinBreakHandler;
		this.plugin = plugin;

		setLayout(new BorderLayout());
		setBackground(ChinBreakHandlerPanel.BACKGROUND_COLOR);

		chinBreakHandler
			.configChanged
			.subscribe(this::onConfigChanged);

		if (chinBreakHandlerPlugin.disposables.containsKey(plugin))
		{
			Disposable seconds = chinBreakHandlerPlugin.disposables.get(plugin);

			if (!seconds.isDisposed())
			{
				seconds.dispose();
			}
		}

		Disposable secondsDisposable = Observable
			.interval(500, TimeUnit.MILLISECONDS)
			.subscribe(this::milliseconds);

		chinBreakHandlerPlugin.disposables.put(plugin, secondsDisposable);

		init();
	}

	public static String formatDuration(Duration duration)
	{
		long seconds = duration.getSeconds();
		long absSeconds = Math.abs(seconds);

		return String.format(
			"%02d:%02d:%02d",
			absSeconds / 3600,
			(absSeconds % 3600) / 60,
			absSeconds % 60);
	}

	private void milliseconds(long ignored)
	{
		Instant now = Instant.now();
		
		Map<Plugin, Instant> startTimes = chinBreakHandler.getStartTimes();

		if (startTimes.containsKey(plugin))
		{
			Duration duration = Duration.between(chinBreakHandler.getStartTimes().get(plugin), now);
			runtimeLabel.setText(formatDuration(duration));
		}

		Map<Plugin, Integer> breaks = chinBreakHandler.getAmountOfBreaks();

		if (breaks.containsKey(plugin))
		{
			breaksLabel.setText(String.valueOf(chinBreakHandler.getAmountOfBreaks().get(plugin)));
		}

		if (!chinBreakHandler.isBreakPlanned(plugin) && !chinBreakHandler.isBreakActive(plugin))
		{
			timeLabel.setText("00:00:00");
			return;
		}

		if (chinBreakHandler.isBreakPlanned(plugin))
		{
			Instant breaktime = chinBreakHandler.getPlannedBreak(plugin);

			if (now.isAfter(breaktime))
			{
				timeLabel.setText("Waiting for plugin");
			}
			else
			{
				Duration duration = Duration.between(now, breaktime);
				timeLabel.setText(formatDuration(duration));
			}
		}
		else if (chinBreakHandler.isBreakActive(plugin))
		{
			Instant breaktime = chinBreakHandler.getActiveBreak(plugin);

			if (now.isAfter(breaktime))
			{
				timeLabel.setText("00:00:00");
			}
			else
			{
				Duration duration = Duration.between(now, breaktime);
				timeLabel.setText(formatDuration(duration));
			}
		}
		else
		{
			timeLabel.setText("-");
		}

		boolean enabled = Boolean.parseBoolean(configManager.getConfiguration(ChinBreakHandlerPlugin.CONFIG_GROUP, sanitizedName(plugin) + "-enabled"));

		if (enabled && chinBreakHandler.getPlugins().get(plugin) && chinBreakHandlerPlugin.isValidBreak(plugin) && !chinBreakHandler.isBreakPlanned(plugin) && !chinBreakHandler.isBreakActive(plugin))
		{
			chinBreakHandlerPlugin.scheduleBreak(plugin);
		}
	}

	private void onConfigChanged(ConfigChanged configChanged)
	{
		if (configChanged == null || !configChanged.getGroup().equals(CONFIG_GROUP) || !configChanged.getKey().contains(sanitizedName(plugin)))
		{
			return;
		}

		if (configChanged.getKey().contains("enabled") && configChanged.getNewValue().equals("false"))
		{
			chinBreakHandler.removePlannedBreak(plugin);
		}

		statusPanel();

		contentPanel.revalidate();
		contentPanel.repaint();
	}

	private void init()
	{
		timeLabel.setFont(new Font("", Font.PLAIN, 20));

		JPanel titleWrapper = new JPanel(new BorderLayout());
		titleWrapper.setBackground(ChinBreakHandlerPanel.BACKGROUND_COLOR);
		titleWrapper.setBorder(new CompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, ChinBreakHandlerPanel.PANEL_BACKGROUND_COLOR),
			BorderFactory.createLineBorder(ChinBreakHandlerPanel.BACKGROUND_COLOR)
		));

		JLabel title = new JLabel();
		title.setText(plugin.getName());
		title.setFont(ChinBreakHandlerPanel.NORMAL_FONT);
		title.setPreferredSize(new Dimension(0, 24));
		title.setForeground(Color.WHITE);
		title.setBorder(new EmptyBorder(0, 8, 0, 0));

		JPanel titleActions = new JPanel(new BorderLayout(3, 0));
		titleActions.setBackground(ChinBreakHandlerPanel.BACKGROUND_COLOR);
		titleActions.setBorder(new EmptyBorder(0, 0, 0, 8));

		JLabel status = new JLabel();
		status.setText("running");
		status.setFont(ChinBreakHandlerPanel.SMALL_FONT.deriveFont(16f));
		status.setForeground(Color.GREEN);

		titleActions.add(status, BorderLayout.EAST);

		titleWrapper.add(title, BorderLayout.CENTER);
		titleWrapper.add(titleActions, BorderLayout.EAST);

		statusPanel();
		infoPanel();

		add(titleWrapper, BorderLayout.NORTH);
		add(contentPanel, BorderLayout.CENTER);
		add(infoPanel, BorderLayout.SOUTH);
	}

	private void statusPanel()
	{
		contentPanel.removeAll();

		contentPanel.setBackground(ChinBreakHandlerPanel.BACKGROUND_COLOR);
		contentPanel.setBorder(new CompoundBorder(
			new CompoundBorder(
				BorderFactory.createMatteBorder(0, 0, 1, 0, ChinBreakHandlerPanel.PANEL_BACKGROUND_COLOR),
				BorderFactory.createLineBorder(ChinBreakHandlerPanel.BACKGROUND_COLOR)
			), BorderFactory.createEmptyBorder(5, 5, 5, 5)));

		GridBagConstraints c = new GridBagConstraints();

		if (!chinBreakHandler.getPlugins().get(plugin) && !chinBreakHandler.isBreakActive(plugin))
		{
			c.insets = new Insets(2, 0, 2, 0);
			c.weightx = 0;
			c.gridx = 0;
			c.gridy = 0;
			contentPanel.add(new JLabel("The plugin will handle break timings"), c);

			return;
		}

		if (chinBreakHandler.getPlugins().get(plugin))
		{
			boolean enabled = Boolean.parseBoolean(configManager.getConfiguration(ChinBreakHandlerPlugin.CONFIG_GROUP, sanitizedName(plugin) + "-enabled"));

			if (!enabled)
			{
				c.insets = new Insets(2, 0, 2, 0);
				c.weightx = 0;
				c.gridx = 0;
				c.gridy = 0;
				contentPanel.add(new JLabel("Breaks are disabled for this plugin"), c);

				return;
			}

			if (chinBreakHandler.getPlugins().get(plugin) && chinBreakHandlerPlugin.isValidBreak(plugin) && !chinBreakHandler.isBreakPlanned(plugin) && !chinBreakHandler.isBreakActive(plugin))
			{
				chinBreakHandlerPlugin.scheduleBreak(plugin);
			}
		}

		JLabel breaking = new JLabel();

		if (chinBreakHandler.isBreakPlanned(plugin))
		{
			breaking.setText("Scheduled break in:");
		}
		else if (chinBreakHandler.isBreakActive(plugin))
		{
			breaking.setText("Taking a break for:");
		}

		c.insets = new Insets(2, 0, 2, 0);
		c.weightx = 0;
		c.gridx = 0;
		c.gridy = 0;
		contentPanel.add(breaking, c);

		c.fill = GridBagConstraints.CENTER;
		c.weightx = 0;
		c.gridx = 0;
		c.gridy = 1;
		contentPanel.add(timeLabel, c);
	}

	private void infoPanel()
	{
		infoPanel.removeAll();

		infoPanel.setBackground(ChinBreakHandlerPanel.BACKGROUND_COLOR);
		infoPanel.setBorder(new CompoundBorder(
			new CompoundBorder(
				BorderFactory.createMatteBorder(0, 0, 1, 0, ChinBreakHandlerPanel.PANEL_BACKGROUND_COLOR),
				BorderFactory.createLineBorder(ChinBreakHandlerPanel.BACKGROUND_COLOR)
			), BorderFactory.createEmptyBorder(5, 5, 5, 5)));

		GridBagConstraints c = new GridBagConstraints();

		c.insets = new Insets(2, 0, 2, 0);
		c.weightx = 2;
		c.gridx = 0;
		c.gridy = 0;

		infoPanel.add(new JLabel("Total runtime"), c);

		c.insets = new Insets(2, 0, 2, 0);
		c.weightx = 1;
		c.gridx = 0;
		c.gridy = 1;

		infoPanel.add(runtimeLabel, c);

		c.insets = new Insets(2, 0, 2, 0);
		c.weightx = 2;
		c.gridx = 1;
		c.gridy = 0;

		infoPanel.add(new JLabel("Amount of breaks"), c);

		c.insets = new Insets(2, 0, 2, 0);
		c.weightx = 1;
		c.gridx = 1;
		c.gridy = 1;

		infoPanel.add(breaksLabel, c);
	}
}
