package com.timeclock.item;

import com.timeclock.auth.BusinessException;
import com.timeclock.item.dto.*;
import com.timeclock.task.TaskService;
import com.timeclock.task.dto.TaskView;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ItemService {
    private final JdbcTemplate jdbc; private final TaskService tasks;
    public ItemService(JdbcTemplate jdbc, TaskService tasks) { this.jdbc=jdbc; this.tasks=tasks; }
    public ItemPage list(String u,String t,String s,int p,int z){ ownedTask(u,t); if(p<1||z<1||z>100)throw val("分页参数无效"); if(s!=null && !s.equals("pending") && !s.equals("completed")) throw val("条目状态无效"); String f=s==null?"":" AND status=?"; Object[] a=s==null?new Object[]{t,z,(p-1)*z}:new Object[]{t,s,z,(p-1)*z}; List<ItemView> i=jdbc.query("SELECT * FROM learning_items WHERE task_id=?"+f+" ORDER BY sort_order,id LIMIT ? OFFSET ?",(r,n)->mapRow(r),a); Long n=jdbc.queryForObject("SELECT COUNT(*) FROM learning_items WHERE task_id=?"+f,Long.class,s==null?new Object[]{t}:new Object[]{t,s}); return new ItemPage(i,p,z,n==null?0:n); }
    @Transactional public ItemView create(String u,String t,ItemCreateRequest q){ownedTask(u,t); title(q.title()); String id=UUID.randomUUID().toString(); Instant now=Instant.now().truncatedTo(ChronoUnit.MICROS); jdbc.update("INSERT INTO learning_items (id,task_id,title,content,analysis,external_url,sort_order,status,created_at,updated_at) VALUES (?,?,?,?,?,?,?,'pending',?,?)",id,t,q.title().trim(),q.content(),q.analysis(),q.externalUrl(),next(t),now,now); return getOwned(u,id);}
    public ItemView get(String u,String id){return getOwned(u,id);}
    @Transactional public ItemView update(String u,String id,ItemUpdateRequest q){ItemView o=getOwned(u,id);String title=q.title()==null?o.title():q.title().trim();title(title);jdbc.update("UPDATE learning_items SET title=?,content=?,analysis=?,external_url=?,updated_at=? WHERE id=?",title,q.content()==null?o.content():q.content(),q.analysis()==null?o.analysis():q.analysis(),q.externalUrl()==null?o.externalUrl():q.externalUrl(),Instant.now().truncatedTo(ChronoUnit.MICROS),id);return getOwned(u,id);}
    public PastePreviewResponse preview(String u,String t,PastePreviewRequest q){ownedTask(u,t);String[] ls=q.text().split("\\R",-1);List<PasteConfirmRequest.PasteCandidate> cs=new ArrayList<>();List<PastePreviewResponse.ErrorLine> es=new ArrayList<>();for(int i=0;i<ls.length;i++){String[] x=ls[i].trim().split("\\|",-1);if(x.length==1&&x[0].isBlank())continue;if(x[0].isBlank()||x[0].length()>255)es.add(new PastePreviewResponse.ErrorLine(i+1,"标题无效"));else cs.add(new PasteConfirmRequest.PasteCandidate(x[0].trim(),x.length>1?x[1].trim():null,x.length>2?x[2].trim():null,x.length>3?x[3].trim():null));}return new PastePreviewResponse(ls.length,cs.size(),es,cs);}
    @Transactional public List<ItemView> confirm(String u,String t,PasteConfirmRequest q){ownedTask(u,t);List<ItemView> out=new ArrayList<>();int n=next(t);Instant now=Instant.now();Set<String> seen=new HashSet<>(jdbc.query("SELECT LOWER(TRIM(title)) FROM learning_items WHERE task_id=?",(r,x)->r.getString(1),t));for(var c:q.candidates()){title(c.title());String k=c.title().trim().toLowerCase(Locale.ROOT);if(!seen.add(k))continue;String id=UUID.randomUUID().toString();jdbc.update("INSERT INTO learning_items (id,task_id,title,content,analysis,external_url,sort_order,status,created_at,updated_at) VALUES (?,?,?,?,?,?,?,'pending',?,?)",id,t,c.title().trim(),c.content(),c.analysis(),c.externalUrl(),n++,now,now);out.add(getOwned(u,id));}return out;}
    public TodayItemsResponse today(String u,String t){TaskView task=ownedTask(u,t);if(!"active".equals(task.status()))return new TodayItemsResponse(task,0,0,List.of());LocalDate d=LocalDate.now(ZoneId.of(task.timezone()));if(d.isBefore(task.startDate())||(task.endDate()!=null&&d.isAfter(task.endDate())))return new TodayItemsResponse(task,0,0,List.of());List<ItemView> a=jdbc.query("SELECT * FROM learning_items WHERE task_id=? AND status='pending' ORDER BY sort_order,id LIMIT ?",(r,n)->mapRow(r),t,task.dailyTargetCount());return new TodayItemsResponse(task,a.size(),0,a.stream().map(x->new TodayItemsResponse.TodayItem(x,true,true)).toList());}
    private TaskView ownedTask(String u,String t){return tasks.get(u,t);} private ItemView getOwned(String u,String id){ItemView x=jdbc.query("SELECT li.* FROM learning_items li JOIN tasks t ON t.id=li.task_id WHERE li.id=? AND t.user_id=?",r->r.next()?map(r):null,id,u);if(x==null)throw new BusinessException("ITEM_NOT_FOUND","条目不存在",404);return x;} private int next(String t){Integer n=jdbc.queryForObject("SELECT COALESCE(MAX(sort_order),0)+1 FROM learning_items WHERE task_id=?",Integer.class,t);return n==null?1:n;} private ItemView mapRow(ResultSet r)throws SQLException{return new ItemView(r.getString("id"),r.getString("task_id"),r.getString("title"),r.getString("content"),r.getString("analysis"),r.getString("external_url"),r.getInt("sort_order"),r.getString("status"),r.getString("solution_text"),r.getTimestamp("completed_at")==null?null:r.getTimestamp("completed_at").toInstant());} private ItemView map(ResultSet r,int n)throws SQLException{return mapRow(r);} private ItemView map(ResultSet r)throws SQLException{return mapRow(r);} private void title(String s){if(s==null||s.isBlank()||s.trim().length()>255)throw val("标题无效");} private BusinessException val(String s){return new BusinessException("VALIDATION_ERROR",s,422);}
}
