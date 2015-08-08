package outputModules.csv.importers;

import java.util.HashMap;
import java.util.Map;

import outputModules.UDGImporter;
import outputModules.csv.CSVWriter;
import udg.useDefGraph.UseOrDefRecord;
import databaseNodes.EdgeTypes;
import databaseNodes.NodeKeys;

public class CSVUDGImporter extends UDGImporter
{

	@Override
	protected void addUseOrDefRecordToDatabase(long symbolNodeId,
			UseOrDefRecord item)
	{
		String edgeType;
		if (item.isDef())
			edgeType = EdgeTypes.DEF;
		else
			edgeType = EdgeTypes.USE;

		long nodeId = CSVWriter.getIdForObject(item.getAstNode());
		CSVWriter.addEdge(nodeId, symbolNodeId, null, edgeType);
	}

	@Override
	protected long createSymbolNode(String identifier)
	{
		long functionId = CSVWriter.getIdForObject(currentFunction);

		Map<String, Object> properties = new HashMap<String, Object>();
		properties.put(NodeKeys.TYPE, "Symbol");
		properties.put(NodeKeys.CODE, identifier);
		properties.put(NodeKeys.FUNCTION_ID, functionId);

		long newNodeId = CSVWriter.addNode(null, properties);
		return newNodeId;
	}
}
