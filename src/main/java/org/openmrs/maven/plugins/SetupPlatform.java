package org.openmrs.maven.plugins;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.components.interactivity.Prompter;
import org.codehaus.plexus.components.interactivity.PrompterException;
import org.openmrs.maven.plugins.model.Server;
import org.openmrs.maven.plugins.utility.AttributeHelper;
import org.openmrs.maven.plugins.utility.PropertyManager;
import org.openmrs.maven.plugins.utility.SDKConstants;

import java.io.File;
import java.util.Properties;

import static org.twdata.maven.mojoexecutor.MojoExecutor.*;

/**
 *
 * @goal setup-platform
 * @requiresProject false
 *
 */
public class SetupPlatform extends AbstractMojo {

    /**
     * Default constructor
     */
    public SetupPlatform() {};

    /**
     * Constructor to use this class in other mojos
     * @param mavenProject - outer project
     * @param mavenSession - outer session
     * @param prompter - outer prompter
     * @param pluginManager - outer pluginManager
     */
    public SetupPlatform(MavenProject mavenProject,
                         MavenSession mavenSession,
                         Prompter prompter,
                         BuildPluginManager pluginManager) {
        this.mavenProject = mavenProject;
        this.mavenSession = mavenSession;
        this.prompter = prompter;
        this.pluginManager = pluginManager;
    }

    /**
     * The project currently being build.
     *
     * @parameter expression="${project}"
     * @required
     */
    private MavenProject mavenProject;

    /**
     * The current Maven session.
     *
     * @parameter expression="${session}"
     * @required
     */
    private MavenSession mavenSession;

    /**
     * Interactive mode param
     *
     * @parameter expression="${interactiveMode}" default-value=false
     */
    private String interactiveMode;

    /**
     * Server id (folder name)
     *
     * @parameter expression="${serverId}"
     */
    private String serverId;

    /**
     * Platform version
     *
     * @parameter expression="${version}" default-value="1.11.2"
     */
    private String version;

    /**
     * DB Driver type
     *
     * @parameter expression="${dbDriver}"
     */
    private String dbDriver;

    /**
     * DB Uri
     *
     * @parameter expression="${dbUri}"
     */
    private String dbUri;

    /**
     * DB User
     *
     * @parameter expression="${dbUser}"
     */
    private String dbUser;

    /**
     * DB Pass
     *
     * @parameter expression="${dbPassword}"
     */
    private String dbPassword;

    /**
     * Component for user prompt
     *
     * @component
     */
    private Prompter prompter;

    /**
     * The Maven BuildPluginManager component.
     *
     * @component
     * @required
     */
    private BuildPluginManager pluginManager;

    /**
     * Create and setup server with following parameters
     * @param server - server instance
     * @param requireDbParams - require db params if not selected
     * @throws MojoExecutionException
     */
    public String setup(Server server, Boolean requireDbParams) throws MojoExecutionException {
        AttributeHelper helper = new AttributeHelper(prompter);
        File omrsPath = new File(System.getProperty("user.home"), SDKConstants.OPENMRS_SERVER_PATH);
        try {
            server.setServerId(helper.promptForNewServerIfMissing(omrsPath.getPath(), server.getServerId()));
        } catch (PrompterException e) {
            getLog().error(e.getMessage());
        }
        File serverPath = new File(omrsPath, server.getServerId());
        if (serverPath.exists()) throw new MojoExecutionException("Server with same id already created");
        Properties executionProps = mavenSession.getExecutionProperties();
        executionProps.put("groupId", SDKConstants.PROJECT_GROUP_ID);
        executionProps.put("artifactId", server.getServerId());
        executionProps.put("package", SDKConstants.PROJECT_PACKAGE);
        executionProps.put("version", server.getVersion());
        executeMojo(
                plugin(
                        groupId(SDKConstants.ARCH_GROUP_ID),
                        artifactId(SDKConstants.ARCH_ARTIFACT_ID),
                        version(SDKConstants.ARCH_VERSION)
                ),
                goal("generate"),
                configuration(
                        element(name("archetypeCatalog"), SDKConstants.ARCH_CATALOG),
                        element(name("interactiveMode"), server.getInteractiveMode()),
                        element(name("archetypeGroupId"), SDKConstants.ARCH_PROJECT_GROUP_ID),
                        element(name("archetypeArtifactId"), SDKConstants.ARCH_PROJECT_ARTIFACT_ID),
                        element(name("archetypeVersion"), SDKConstants.ARCH_PROJECT_VERSION),
                        element(name("basedir"), omrsPath.getPath())
                ),
                executionEnvironment(mavenProject, mavenSession, pluginManager)
        );
        getLog().info("Server created successfully, path: " + serverPath.getPath());
        if ((server.getDbDriver() != null) ||
                (server.getDbUser() != null) ||
                (server.getDbPassword() != null) ||
                (server.getDbUri() != null) ||
                requireDbParams) {
            File propertiesFile = new File(serverPath.getPath(), SDKConstants.OPENMRS_SERVER_PROPERTIES);
            PropertyManager properties = new PropertyManager(propertiesFile.getPath(), getLog());
            try {
                // String defaultDriver = "mysql";
                server.setDbDriver(helper.promptForValueIfMissingWithDefault(dbDriver, "dbDriver", "mysql"));
                // if (server.getDbDriver() == null) server.setDbDriver(prompter.prompt("Define value for property 'dbDriver': (default: 'mysql')"));
                // if (server.getDbDriver().equals("")) server.setDbDriver(defaultDriver);
                String defaultUri = SDKConstants.URI_MYSQL;
                if ((server.getDbDriver().equals("postgresql")) || (server.getDbDriver().equals(SDKConstants.DRIVER_POSTGRESQL))) {
                    properties.setParam("dbDriver", SDKConstants.DRIVER_POSTGRESQL);
                    defaultUri = SDKConstants.URI_POSTGRESQL;
                }
                else if ((server.getDbDriver().equals("h2")) || (server.getDbDriver().equals(SDKConstants.DRIVER_H2))) {
                    properties.setParam("dbDriver", SDKConstants.DRIVER_H2);
                    defaultUri = SDKConstants.URI_H2;
                }
                else if (server.getDbDriver().equals("mysql")) {
                    properties.setParam("dbDriver", SDKConstants.DRIVER_MYSQL);
                }
                else properties.setParam("dbDriver", server.getDbDriver());

                server.setDbUri(helper.promptForValueIfMissingWithDefault(dbUri, "dbUri", defaultUri));
                //if (server.getDbUri() == null) server.setDbUri(prompter.prompt("Define value for property 'dbUri': (default: '" + defaultUri + "')"));
                //if (server.getDbUri().equals("")) server.setDbUri(defaultUri);
                String defaultUser = "root";
                server.setDbUser(helper.promptForValueIfMissingWithDefault(dbUser, "dbUser", defaultUser));
                //if (server.getDbUser() == null) server.setDbUser(prompter.prompt("Define value for property 'dbUser': (default: '" + defaultUser + "')"));
                //if (server.getDbUser().equals("")) server.setDbUser(defaultUser);
                server.setDbPassword(helper.promptForValueIfMissing(dbPassword, "dbPassword"));
                //if (server.getDbPassword() == null) server.setDbPassword(prompter.prompt("Define value for property 'dbPassword'"));
                properties.setParam("dbDriver", server.getDbDriver());
                properties.setParam("dbUser", server.getDbUser());
                properties.setParam("dbPassword", server.getDbPassword());
                properties.setParam("dbUri", server.getDbUri());
                properties.apply();
            } catch (PrompterException e) {
                getLog().error(e.getMessage());
            }
        }
        return serverPath.getPath();
    }

    public void execute() throws MojoExecutionException {
        Server server = new Server.ServerBuilder()
                .setServerId(serverId)
                .setVersion(version)
                .setDbDriver(dbDriver)
                .setDbUser(dbUser)
                .setDbUri(dbUri)
                .setDbPassword(dbPassword)
                .setInteractiveMode(interactiveMode)
                .build();
        setup(server, false);
    }
}