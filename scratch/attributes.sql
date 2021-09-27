select c.Category, c.Attribute,
       b.AttrType, b.ValDate, b.ValInt, b.ValLong, b.ValReal, b.ValStr
from DTreeCore a inner join LLAttrData b on a.DataID = b.ID
                 inner join
     ( select *
       from Facade_Attributes) c
     on b.DefID = c.CategoryId
         and b.DefVerN = c.CategoryVersion
         and b.AttrID = c.AttributeIndex
where a.DataID = 32379
  and b.VerNum = a.VersionNum
order by c.Category, c.AttributeIndex