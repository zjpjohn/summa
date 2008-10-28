package dk.statsbiblioteket.summa.control.client.shell;

import dk.statsbiblioteket.summa.control.api.ClientConnection;
import dk.statsbiblioteket.summa.control.api.NoSuchServiceException;
import dk.statsbiblioteket.summa.control.bundle.BundleSpecBuilder;
import dk.statsbiblioteket.summa.common.shell.RemoteCommand;
import dk.statsbiblioteket.summa.common.shell.ShellContext;
import dk.statsbiblioteket.util.rpc.ConnectionManager;

import java.io.ByteArrayInputStream;
import java.util.Map;

import java.io.Serializable;

/**
 * {@link dk.statsbiblioteket.summa.common.shell.Command} implementation
 * for the client shell to print out the contents of either the client's
 * bundle spec, or the bundle spec of a deployed service
 */
public class SpecCommand extends RemoteCommand<ClientConnection> {

    private String clientAddress;

    public SpecCommand(ConnectionManager<ClientConnection> connMgr,
                         String clientAddress) {
        super("spec", "Print the bundle spec of the client or a deployed "
                      + "service", connMgr);

        setUsage("spec [options] [service_id]...");

        installOption("p", "pretty", false, "Pretty print the spec contents");
        installOption("f", "files", false, "Whether to print the file list in "
                                           + "the pretty-print mode");

        this.clientAddress = clientAddress;
    }

    public void invoke(ShellContext ctx) throws Exception {
        ClientConnection client = getConnection(clientAddress);

        String[] services = getArguments();

        try {
            if (services.length == 0) {
                String spec = client.getBundleSpec(null);
                ctx.info("Client spec:");
                ctx.info(spec);
            } else {
                for (String service : services) {
                    ctx.info("Service spec for '"+service+"':");
                    String spec;
                    try {
                        spec = client.getBundleSpec(service);
                        if (hasOption("pretty")) {
                            prettyPrint(spec, ctx);
                        } else {
                            ctx.info(spec);
                        }
                    } catch (NoSuchServiceException e) {
                        ctx.error("No such service '"+service+"'");
                    }
                }
            }
        } finally {
            releaseConnection();
        }
    }

    private void prettyPrint(String spec, ShellContext ctx) {
        BundleSpecBuilder builder = BundleSpecBuilder.open(
                                     new ByteArrayInputStream(spec.getBytes()));

        String msg =
        "Description:\n" + builder.getDescription() + "\n\n";

        msg += "Metadata:\n"
        + "\tInstance id : " + builder.getInstanceId() + "\n"
        + "\tBundle id   : " + builder.getBundleId() + "\n"
        + "\tMain jar    : " + builder.getMainJar() + "\n"
        + "\tMain class  : " + builder.getMainClass() + "\n"
        + "\n";

        msg += "JVM Properties:\n";
        for (Map.Entry<String, Serializable> entry: builder.getProperties()) {
            msg += "\t" + entry.getKey() + " = " + entry.getValue() + "\n";
        }
        msg += "\n";

        msg+= "Public API:\n";
        for (String api : builder.getApi()) {
            msg += "\t" + api + "\n";
        }
        msg += "\n";

        if (hasOption("files")) {
            msg += "Files:\n";
            for (String file : builder.getFiles()) {
                msg += "\t" + file + "\n";
            }
            msg += "\n";
        }

        ctx.info(msg);
    }
}
