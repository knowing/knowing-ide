/*                                                               *\
 ** |¯¯|/¯¯/|¯¯ \|¯¯| /¯¯/\¯¯\'|¯¯|  |¯¯||¯¯||¯¯ \|¯¯| /¯¯/|__|  **
 ** | '| '( | '|\  '||  |  | '|| '|/\| '|| '|| '|\  '||  | ,---, **
 ** |__|\__\|__|'|__| \__\/__/'|__,/\'__||__||__|'|__| \__\/__|  **
 **                                                              **
 ** Knowing Framework                                            **
 ** Apache License - http://www.apache.org/licenses/             **
 ** LMU Munich - Database Systems Group                          **
 ** http://www.dbs.ifi.lmu.de/                                   **
\*                                                               */
package de.lmu.ifi.dbs.knowing.debug.ui.adapter;

import org.eclipse.core.runtime.IAdapterFactory;
import org.eclipse.ui.model.IWorkbenchAdapter;

import de.lmu.ifi.dbs.knowing.core.model.IProperty;

/**
 * @author Nepomuk Seiler
 * @version 0.1
 * @since 26.06.2011
 *
 */
public class PropertyAdapterFactory implements IAdapterFactory {

	private static final Class[] types = new Class[] { IWorkbenchAdapter.class , IWorkbenchColumnAdapter.class};
	
	@Override
	public Object getAdapter(Object adaptableObject, Class adapterType) {
		if(!(adaptableObject instanceof IProperty))
			return null;
		return new PropertyAdapter();
	}

	@Override
	public Class[] getAdapterList() {
		return types;
	}

}
