package nz.co.jammehcow.jenkinsdiscord;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.*;
import hudson.scm.ChangeLogSet;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;
import jenkins.model.JenkinsLocationConfiguration;
import nz.co.jammehcow.jenkinsdiscord.exception.WebhookException;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/**
 * Author: jammehcow.
 * Date: 22/04/17.
 */

public class WebhookPublisher extends Notifier {
    private final String webhookURL;
    private static final String NAME = "Discord Notifier";
    private static final String VERSION = "1.0.0";

    @DataBoundConstructor
    public WebhookPublisher(String webhookURL) { this.webhookURL = webhookURL; }

    public String getWebhookURL() { return this.webhookURL; }

    @Override
    public boolean needsToRunAfterFinalized() { return true; }

    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {
        JenkinsLocationConfiguration globalConfig = new JenkinsLocationConfiguration();

        if (this.webhookURL.isEmpty()) {
            listener.getLogger().println("The Discord webhook is not set!");
            return true;
        } else if (globalConfig.getUrl() == null || globalConfig.getUrl().isEmpty()) {
            listener.getLogger().println("Your Jenkins URL is not set (or is set to localhost)!");
            return true;
        }

        StringBuilder changesList = new StringBuilder();

        for (Object o : build.getChangeSet().getItems()) {
            ChangeLogSet.Entry en = (ChangeLogSet.Entry) o;
            changesList.append(formatSCMChange(en));
        }

        String buildUrl = globalConfig.getUrl() + build.getUrl();
        String artifactsUrl = buildUrl + "artifact/";
        String consoleUrl =  buildUrl + "console";

        boolean buildStatus = build.getResult().isBetterOrEqualTo(Result.SUCCESS);

        DiscordWebhook wh = new DiscordWebhook(this.webhookURL);
        wh.setTitle(build.getProject().getDisplayName() + " #" + build.getId());
        wh.setDescription(
                "**Build:**  #" + build.getId() +
                "\n**Status:**  " + (build.getResult().toString().toLowerCase()) +
                "\n**Log:**  " + consoleUrl +
                "\n**Download:**\n" + artifactsUrl +
                ((changesList.length() != 0) ? "\n**Changes:**\n" + changesList.toString() : "")
        );
        wh.setStatus(buildStatus);
        wh.setURL(buildUrl);
        wh.setFooter("Jenkins v" + build.getHudsonVersion() + ", " + getDescriptor().getDisplayName() + " v" + getDescriptor().getVersion());

        try { wh.send(); }
        catch (WebhookException e) { e.printStackTrace(); }

        return true;
    }


    public BuildStepMonitor getRequiredMonitorService() { return BuildStepMonitor.NONE; }


    @Override
    public DescriptorImpl getDescriptor() { return (DescriptorImpl) super.getDescriptor(); }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {


        public boolean isApplicable(Class<? extends AbstractProject> aClass) { return true; }

        public FormValidation doCheckWebhookURL(@QueryParameter String value) {
            if (!value.matches("https://discordapp\\.com/api/webhooks/\\d{18}/(\\w|-|_)*(/?)"))
                return FormValidation.error("Please enter a valid Discord webhook URL.");
            return FormValidation.ok();
        }

        public String getDisplayName() { return NAME; }

        public String getVersion() { return VERSION; }
    }

    private static String formatSCMChange(ChangeLogSet.Entry entry) {
        return "   - ``" + entry.getCommitId() + "`` *" + entry.getMsg() + " - " + entry.getAuthor().getFullName() + "*\n";
    }
}
