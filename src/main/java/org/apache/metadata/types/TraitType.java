/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.metadata.types;

import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.apache.metadata.IStruct;
import org.apache.metadata.MetadataException;
import org.apache.metadata.Struct;
import org.apache.metadata.storage.DownCastStructInstance;
import org.apache.metadata.storage.StructInstance;

import java.util.*;

public class TraitType extends StructType implements Comparable<TraitType> {

    public final ImmutableList<String> superTraits;
    protected final ImmutableList<AttributeInfo> immediateAttrs;
    protected ImmutableMap<String, List<Path>> superTypePaths;
    protected ImmutableMap<String, Path> pathNameToPathMap;

    /**
     * Used when creating a TraitType, to support recursive Structs.
     */
    TraitType(ITypeBrowser typeSystem, String name, ImmutableList<String> superTraits, int numFields) {
        super(typeSystem, name, numFields);
        this.superTraits = superTraits;
        this.immediateAttrs = null;
    }

    TraitType(ITypeBrowser typeSystem, String name, ImmutableList<String> superTraits, AttributeInfo... fields)
            throws MetadataException {
        super(typeSystem, name, superTraits, fields);
        this.superTraits = superTraits == null ? ImmutableList.<String>of() : superTraits;
        this.immediateAttrs = ImmutableList.<AttributeInfo>copyOf(fields);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    protected FieldMapping constructFieldMapping(ImmutableList<String> superTraits,
                                                 AttributeInfo... fields)
            throws MetadataException {

        Map<String,AttributeInfo> fieldsMap = new LinkedHashMap<String, AttributeInfo>();
        Map<String, Integer> fieldPos = new HashMap<String, Integer>();
        Map<String, Integer> fieldNullPos = new HashMap<String, Integer>();
        int numBools = 0;
        int numBytes = 0;
        int numShorts = 0;
        int numInts = 0;
        int numLongs = 0;
        int numFloats = 0;
        int numDoubles = 0;
        int numBigInts = 0;
        int numBigDecimals = 0;
        int numDates = 0;
        int numStrings = 0;
        int numArrays = 0;
        int numMaps = 0;
        int numStructs = 0;

        Map<String, List<Path>> superTypePaths = new HashMap<String, List<Path>>();
        Map<String, Path> pathNameToPathMap = new HashMap<String, Path>();
        Queue<Path> queue = new LinkedList<Path>();
        queue.add(new Node(getName()));
        while(!queue.isEmpty()) {
            Path currentPath = queue.poll();
            TraitType superType = currentPath.typeName == getName() ? this :
                    (TraitType) typeSystem.dataType(currentPath.typeName);

            pathNameToPathMap.put(currentPath.pathName, currentPath);
            if ( superType != this ) {
                List<Path> typePaths = superTypePaths.get(superType.getName());
                if ( typePaths == null ) {
                    typePaths = new ArrayList<Path>();
                    superTypePaths.put(superType.getName(), typePaths);
                }
                typePaths.add(currentPath);
            }

            ImmutableList<AttributeInfo> superTypeFields = superType == this ?
                    ImmutableList.<AttributeInfo>copyOf(fields) : superType.immediateAttrs;

            Set<String> immediateFields = new HashSet<String>();

            for(AttributeInfo i : superTypeFields) {
                if ( superType == this && immediateFields.contains(i.name) ) {
                    throw new MetadataException(
                            String.format("Struct defintion cannot contain multiple fields with the same name %s",
                                    i.name));
                }
                
                String attrName = i.name;
                if ( fieldsMap.containsKey(attrName)) {
                    attrName = currentPath.addOverrideAttr(attrName);
                }
                
                fieldsMap.put(attrName, i);
                fieldNullPos.put(attrName, fieldNullPos.size());
                if ( i.dataType() == DataTypes.BOOLEAN_TYPE ) {
                    fieldPos.put(attrName, numBools);
                    numBools++;
                } else if ( i.dataType() == DataTypes.BYTE_TYPE ) {
                    fieldPos.put(attrName, numBytes);
                    numBytes++;
                } else if ( i.dataType() == DataTypes.SHORT_TYPE ) {
                    fieldPos.put(attrName, numShorts);
                    numShorts++;
                } else if ( i.dataType() == DataTypes.INT_TYPE ) {
                    fieldPos.put(attrName, numInts);
                    numInts++;
                } else if ( i.dataType() == DataTypes.LONG_TYPE ) {
                    fieldPos.put(attrName, numLongs);
                    numLongs++;
                } else if ( i.dataType() == DataTypes.FLOAT_TYPE ) {
                    fieldPos.put(attrName, numFloats);
                    numFloats++;
                } else if ( i.dataType() == DataTypes.DOUBLE_TYPE ) {
                    fieldPos.put(attrName, numDoubles);
                    numDoubles++;
                } else if ( i.dataType() == DataTypes.BIGINTEGER_TYPE ) {
                    fieldPos.put(attrName, numBigInts);
                    numBigInts++;
                } else if ( i.dataType() == DataTypes.BIGDECIMAL_TYPE ) {
                    fieldPos.put(attrName, numBigDecimals);
                    numBigDecimals++;
                } else if ( i.dataType() == DataTypes.DATE_TYPE ) {
                    fieldPos.put(attrName, numDates);
                    numDates++;
                } else if ( i.dataType() == DataTypes.STRING_TYPE ) {
                    fieldPos.put(attrName, numStrings);
                    numStrings++;
                } else if ( i.dataType().getTypeCategory() == DataTypes.TypeCategory.ARRAY ) {
                    fieldPos.put(attrName, numArrays);
                    numArrays++;
                } else if ( i.dataType().getTypeCategory() == DataTypes.TypeCategory.MAP ) {
                    fieldPos.put(attrName, numMaps);
                    numMaps++;
                } else if ( i.dataType().getTypeCategory() == DataTypes.TypeCategory.STRUCT ) {
                    fieldPos.put(attrName, numStructs);
                    numStructs++;
                } else {
                    throw new MetadataException(String.format("Unknown datatype %s", i.dataType()));
                }
            }

            for(String sT : superType == this ? superTraits : superType.superTraits) {
                queue.add(new Path(sT, currentPath));
            }
        }

        this.superTypePaths = ImmutableMap.copyOf(superTypePaths);
        this.pathNameToPathMap = ImmutableMap.copyOf(pathNameToPathMap);

        return new FieldMapping(fieldsMap,
                fieldPos,
                fieldNullPos,
                numBools,
                numBytes,
                numShorts,
                numInts,
                numLongs,
                numFloats,
                numDoubles,
                numBigInts,
                numBigDecimals,
                numDates,
                numStrings,
                numArrays,
                numMaps,
                numStructs);
    }

    @Override
    public DataTypes.TypeCategory getTypeCategory() {
        return DataTypes.TypeCategory.TRAIT;
    }

    protected Map<String, String> constructDowncastFieldMap(TraitType subType, Path pathToSubType) {

        String pathToSubTypeName = pathToSubType.pathAfterThis;
        /*
         * the downcastMap;
         */
        Map<String, String> dCMap = new HashMap<String, String>();
        Iterator<Path> itr = pathIterator();
        while(itr.hasNext()) {
            Path p = itr.next();
            Path pInSubType = subType.pathNameToPathMap.get(p.pathName + "." + pathToSubTypeName);

            if ( pInSubType.hiddenAttributeMap != null ) {
                for(Map.Entry<String, String> e : pInSubType.hiddenAttributeMap.entrySet()) {
                    String mappedInThisType =
                            p.hiddenAttributeMap != null ? p.hiddenAttributeMap.get(e.getKey()) : null;
                    if ( mappedInThisType == null ) {
                        dCMap.put(e.getKey(), e.getValue());
                    } else {
                        dCMap.put(mappedInThisType, e.getValue());
                    }
                }
            }
        }
        return dCMap;
    }

    public IStruct castAs(IStruct s, String superTypeName) throws MetadataException {

        if ( !superTypePaths.containsKey(superTypeName) ) {
            throw new MetadataException(String.format("Cannot downcast to %s from type %s", superTypeName, getName()));
        }

        if (s != null) {
            if (s.getTypeName() != getName()) {
                throw new MetadataException(
                        String.format("Downcast called on wrong type %s, instance type is %s",
                                getName(), s.getTypeName()));
            }

            List<Path> pathToSuper = superTypePaths.get(superTypeName);
            if ( pathToSuper.size() > 1 ) {
                throw new MetadataException(
                        String.format("Cannot downcast called to %s, from %s: there are multiple paths to SuperType",
                                superTypeName, getName()));
            }

            TraitType superType = (TraitType) typeSystem.dataType(superTypeName);
            Map<String, String> downCastMap = superType.constructDowncastFieldMap(this, pathToSuper.get(0));
            return new DownCastStructInstance(superTypeName,
                    new DownCastFieldMapping(ImmutableMap.copyOf(downCastMap)),
                    s);
        }

        return null;
    }

    public Iterator<Path> pathIterator() {
        return new PathItr();
    }

    @Override
    public int compareTo(TraitType o) {
        String oName = o.getName();
        if ( superTraits.contains(oName) ) {
            return 1;
        } else if ( o.superTraits.contains(getName())) {
            return -1;
        } else {
            return getName().compareTo(oName);
        }
    }

    class PathItr implements Iterator<Path> {

        Queue<Path> pathQueue;

        PathItr() {
            pathQueue = new LinkedList<Path>();
            pathQueue.add(pathNameToPathMap.get(getName()));
        }

        @Override
        public boolean hasNext() {
            return !pathQueue.isEmpty();
        }

        @Override
        public Path next() {
            Path p = pathQueue.poll();
            TraitType t = (TraitType) typeSystem.dataType(p.typeName);
            if ( t.superTraits != null ) {
                for(String sT : t.superTraits) {
                    String nm = sT + "." + p.pathName;
                    pathQueue.add(pathNameToPathMap.get(nm));
                }
            }
            return p;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    static class Path {
        public final String typeName;
        private final Path subTypePath;
        public final String pathName;
        public final String pathAfterThis;
        /*
         * name mapping for attributes hidden by a SubType.
         */
        Map<String, String> hiddenAttributeMap;

        Path(String typeName, Path childPath) throws MetadataException {
            this.typeName = typeName;
            this.subTypePath = childPath;
            if ( childPath.contains(typeName) ) {
                throw new CyclicTypeDefinition(this);
            }
            pathName = String.format("%s.%s", typeName, childPath.pathName);
            pathAfterThis = childPath.pathName;
        }

        Path(String typeName) {
            assert getClass() == Node.class;
            this.typeName = typeName;
            this.subTypePath = null;
            pathName = typeName;
            pathAfterThis = null;
        }

        public boolean contains(String typeName) {
            return this.typeName.equals(typeName) || (subTypePath != null && subTypePath.contains(typeName));
        }

        public String pathString(String nodeSep) {

            StringBuilder b = new StringBuilder();
            Path p = this;

            while ( p != null ) {
                b.append(p.typeName);
                p = p.subTypePath;
                if ( p != null ) {
                    b.append(nodeSep);
                }
            }
            return b.toString();
        }

        String addOverrideAttr(String name) {
            hiddenAttributeMap = hiddenAttributeMap == null ? new HashMap<String, String>() : hiddenAttributeMap;
            String oName = pathName + "." + name;
            hiddenAttributeMap.put(name, oName);
            return oName;
        }
    }

    static class Node extends Path {
        Node(String typeName) {
            super(typeName);
        }
    }

    static class CyclicTypeDefinition extends MetadataException {

        CyclicTypeDefinition(Path p) {
            super(String.format("Cycle in Type Definition %s", p.pathString(" -> ")));
        }
    }
}
