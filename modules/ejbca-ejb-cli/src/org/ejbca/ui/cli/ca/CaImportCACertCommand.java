package org.ejbca.ui.cli.ca;

import java.io.IOException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.util.Collection;
import java.util.List;

import org.cesecore.authorization.AuthorizationDeniedException;
import org.cesecore.authorization.rules.AccessRuleNotFoundException;
import org.cesecore.certificates.ca.CAExistsException;
import org.cesecore.keys.token.IllegalCryptoTokenException;
import org.cesecore.roles.RoleExistsException;
import org.cesecore.roles.RoleNotFoundException;
import org.cesecore.util.CertTools;
import org.cesecore.util.CryptoProviderTools;
import org.ejbca.ui.cli.ErrorAdminCommandException;
import org.ejbca.util.CliTools;

/**
 * Imports a PEM file and creates a new external CA representation from it.
 */
public class CaImportCACertCommand extends BaseCaAdminCommand {

	public String getMainCommand() { return MAINCOMMAND; }
	public String getSubCommand() { return "importcacert"; }
	public String getDescription() { return "Imports a PEM file and creates a new external CA representation from it"; }

    public void execute(String[] args) throws ErrorAdminCommandException {
        args = parseUsernameAndPasswordFromArgs(args);
        
        if (args.length < 3) {
        	getLogger().info("Description: " + getDescription());
        	getLogger().info("Usage: " + getCommand() + " <CA name> <PEM file> [-initauthorization] [-superadmincn SuperAdmin]\n");
			getLogger().info("Add the argument initauthorization if you are importing an initial administration CA, and this will be the first CA in your system. Only used during installation when there is no local AdminCA on the EJBCA instance, but an external CA is used for administration.\n");
    		getLogger().info("Adding the parameters '-superadmincn SuperAdmin' makes an initial CA use the common name SuperAdmin when initializing the authorization module with an initial super administrator. Note only used together with -initauthorization when creating initial CA.");
			return;
		}
		String caName = args[1];
		String pemFile = args[2];
		List<String> argsList = CliTools.getAsModifyableList(args);
		boolean initAuth = argsList.remove("-initauthorization");

		int superAdminCNInd = argsList.indexOf("-superadmincn");
		String superAdminCN = null;
		if (superAdminCNInd > -1) {
			if (argsList.size() <= (superAdminCNInd+1)) {
				getLogger().info("Use -superadmincn SuperAdminCN");
				return;
			}
			superAdminCN = argsList.get(superAdminCNInd+1);
			argsList.remove(superAdminCN);
			argsList.remove("-superadmincn");
		}

		try {
			CryptoProviderTools.installBCProvider();
			Collection<Certificate> certs = CertTools.getCertsFromPEM(pemFile);
			if (certs.size() != 1) {
				throw new ErrorAdminCommandException("PEM file must only contain one CA certificate, this PEM file contains "+certs.size()+".");
			}
			if (initAuth) {
				String subjectdn = CertTools.getSubjectDN(certs.iterator().next());
				Integer caid = Integer.valueOf(subjectdn.hashCode());
				initAuthorizationModule(getAdmin(cliUserName, cliPassword), caid.intValue(), superAdminCN);
			}
			ejb.getCAAdminSession().importCACertificate(getAdmin(cliUserName, cliPassword), caName, certs);
			getLogger().info("Imported CA "+caName);			
		} catch (CertificateException e) {
			getLogger().error(e.getMessage());
		} catch (IOException e) {
			getLogger().error(e.getMessage());
		} catch (CAExistsException e) {
			getLogger().error(e.getMessage());
		} catch (IllegalCryptoTokenException e) {
			getLogger().error(e.getMessage());
		} catch (AuthorizationDeniedException e) {
			getLogger().error(e.getMessage());
		} catch (AccessRuleNotFoundException e) {
			getLogger().error(e.getMessage());
		} catch (RoleExistsException e) {
			getLogger().error(e.getMessage());
		} catch (RoleNotFoundException e) {
			getLogger().error(e.getMessage());
		}
	}
}
