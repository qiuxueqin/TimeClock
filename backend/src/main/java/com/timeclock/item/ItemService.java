package com.timeclock.item;

import com.timeclock.auth.BusinessException;
import com.timeclock.common.IdempotencyService;
import com.timeclock.item.dto.*;
import com.timeclock.task.TaskService;
import com.timeclock.task.dto.TaskView;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
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
    private final JdbcTemplate jdbc; private final TaskService tasks; private final IdempotencyService idempotency; private final Clock clock;
    @org.springframework.beans.factory.annotation.Autowired
    public ItemService(JdbcTemplate jdbc, TaskService tasks, IdempotencyService idempotency) { this(jdbc, tasks, idempotency, Clock.systemUTC()); }
    ItemService(JdbcTemplate jdbc, TaskService tasks, IdempotencyService idempotency, Clock clock) { this.jdbc=jdbc; this.tasks=tasks; this.idempotency=idempotency; this.clock=clock; }
    public ItemPage list(String u,String t,String s,int p,int z){ ownedTask(u,t); if(p<1||z<1||z>100)throw val("分页参数无效"); if(s!=null && !s.equals("pending") && !s.equals("completed")) throw val("条目状态无效"); String f=s==null?"":" AND status=?"; Object[] a=s==null?new Object[]{t,z,(p-1)*z}:new Object[]{t,s,z,(p-1)*z}; List<ItemView> i=jdbc.query("SELECT * FROM learning_items WHERE task_id=?"+f+" ORDER BY sort_order,id LIMIT ? OFFSET ?",(r,n)->mapRow(r),a); Long n=jdbc.queryForObject("SELECT COUNT(*) FROM learning_items WHERE task_id=?"+f,Long.class,s==null?new Object[]{t}:new Object[]{t,s}); return new ItemPage(i,p,z,n==null?0:n); }
    @Transactional public ItemView create(String u,String t,ItemCreateRequest q){ownedTask(u,t); title(q.title()); String id=UUID.randomUUID().toString(); Instant now=Instant.now(clock).truncatedTo(ChronoUnit.MICROS); jdbc.update("INSERT INTO learning_items (id,task_id,title,content,analysis,external_url,sort_order,status,created_at,updated_at) VALUES (?,?,?,?,?,?,?,'pending',?,?)",id,t,q.title().trim(),q.content(),q.analysis(),q.externalUrl(),next(t),now,now); return getOwned(u,id);}
    public ItemView get(String u,String id){return getOwned(u,id);}
    @Transactional public ItemView update(String u,String id,ItemUpdateRequest q){ItemView o=getOwned(u,id);String title=q.title()==null?o.title():q.title().trim();title(title);jdbc.update("UPDATE learning_items SET title=?,content=?,analysis=?,external_url=?,updated_at=? WHERE id=?",title,q.content()==null?o.content():q.content(),q.analysis()==null?o.analysis():q.analysis(),q.externalUrl()==null?o.externalUrl():q.externalUrl(),Instant.now(clock).truncatedTo(ChronoUnit.MICROS),id);return getOwned(u,id);}
    public PastePreviewResponse preview(String u,String t,PastePreviewRequest q){ownedTask(u,t);String[] ls=q.text().split("\\R",-1);List<PasteConfirmRequest.PasteCandidate> cs=new ArrayList<>();List<PastePreviewResponse.ErrorLine> es=new ArrayList<>();for(int i=0;i<ls.length;i++){String[] x=ls[i].trim().split("\\|",-1);if(x.length==1&&x[0].isBlank())continue;if(x[0].isBlank()||x[0].length()>255)es.add(new PastePreviewResponse.ErrorLine(i+1,"标题无效"));else cs.add(new PasteConfirmRequest.PasteCandidate(x[0].trim(),x.length>1?x[1].trim():null,x.length>2?x[2].trim():null,x.length>3?x[3].trim():null));}return new PastePreviewResponse(ls.length,cs.size(),es,cs);}
    @Transactional public List<ItemView> confirm(String u,String t,String key,PasteConfirmRequest q){ownedTask(u,t);List<ItemView> prior=idempotency.beginList(u,t,"paste-confirm",key,q,ItemView.class);if(prior!=null)return prior;List<ItemView> out=new ArrayList<>();int n=next(t);Instant now=Instant.now(clock);Set<String> seen=new HashSet<>(jdbc.query("SELECT LOWER(TRIM(title)) FROM learning_items WHERE task_id=?",(r,x)->r.getString(1),t));for(var c:q.candidates()){title(c.title());String k=c.title().trim().toLowerCase(Locale.ROOT);if(!seen.add(k))continue;String id=UUID.randomUUID().toString();jdbc.update("INSERT INTO learning_items (id,task_id,title,content,analysis,external_url,sort_order,status,created_at,updated_at) VALUES (?,?,?,?,?,?,?,'pending',?,?)",id,t,c.title().trim(),c.content(),c.analysis(),c.externalUrl(),n++,now,now);out.add(getOwned(u,id));}idempotency.complete(u,t,"paste-confirm",key,out);return out;}
    public TodayItemsResponse today(String u,String t){TaskView task=ownedTask(u,t);if(!"active".equals(task.status()))return new TodayItemsResponse(task,0,0,List.of());ZoneId zone=ZoneId.of(task.timezone());LocalDate d=LocalDate.now(clock.withZone(zone));if(d.isBefore(task.startDate())||(task.endDate()!=null&&d.isAfter(task.endDate())))return new TodayItemsResponse(task,0,0,List.of());int completed=jdbc.queryForObject("SELECT COUNT(*) FROM learning_items WHERE task_id=? AND status='completed' AND completed_at >= ? AND completed_at < ?",Integer.class,t,d.atStartOfDay(zone).toInstant(),d.plusDays(1).atStartOfDay(zone).toInstant());int pending=jdbc.queryForObject("SELECT COUNT(*) FROM learning_items WHERE task_id=? AND status='pending'",Integer.class,t);int planned=Math.min(task.dailyTargetCount(),pending+completed);List<ItemView> a=jdbc.query("SELECT * FROM learning_items WHERE task_id=? AND status='pending' ORDER BY sort_order,id LIMIT ?",(r,n)->mapRow(r),t,planned);return new TodayItemsResponse(task,planned,completed,a.stream().map(x->new TodayItemsResponse.TodayItem(x,true,true)).toList());}

    public SubmissionView submission(String u,String id){ ItemView item=getOwned(u,id); return new SubmissionView(item.id(),item.solutionText()==null?"":item.solutionText(),item.status()); }

    @Transactional public SubmissionView saveSolution(String u,String id,SolutionRequest q){ ItemView item=getOwned(u,id); String text=q.solutionContent(); jdbc.update("UPDATE learning_items SET solution_text=?, updated_at=? WHERE id=?",text,Instant.now(clock).truncatedTo(ChronoUnit.MICROS),id); return new SubmissionView(id,text,item.status()); }

    /** 完成条目：有效文字题解 + 今日计划/顺延项；条目、当日进度与自动打卡在同一事务内更新（DEC-07/08）。
     *  锁序固定为 tasks 行 X 锁 -> 幂等键写入：idempotency_keys.fk_idempotency_task 的外键检查
     *  会对父行 tasks 加 S 锁，若先插幂等键再 FOR UPDATE 升级 X 锁，并发下必然死锁（1213）。 */
    @Transactional public CompletionResponse complete(String u,String id,String key,SolutionRequest q){
        ItemView existing=getOwned(u,id); String taskId=existing.taskId();
        TaskView task=ownedTask(u,taskId);
        lockTask(u,taskId);
        CompletionResponse replay=idempotency.begin(u,taskId,"complete",key,q,CompletionResponse.class); if(replay!=null)return replay;
        String text=q.solutionContent();
        if(text==null||text.trim().isEmpty())throw new BusinessException("SOLUTION_REQUIRED","题解不能为空",422);
        existing=lockItem(u,id);
        if("completed".equals(existing.status()))throw new BusinessException("ITEM_ALREADY_COMPLETED","条目已经完成",409);
        if(!"active".equals(task.status()))throw new BusinessException("TASK_NOT_ACTIVE","任务尚未启用",409);
        ZoneId zone=ZoneId.of(task.timezone()); LocalDate date=LocalDate.now(clock.withZone(zone));
        boolean planDay=!date.isBefore(task.startDate())&&(task.endDate()==null||!date.isAfter(task.endDate()));
        List<ItemView> planned=planDay?jdbc.query("SELECT * FROM learning_items WHERE task_id=? AND status='pending' ORDER BY sort_order,id LIMIT ? FOR UPDATE",(r,n)->mapRow(r),taskId,task.dailyTargetCount()):List.of();
        if(planned.stream().noneMatch(x->x.id().equals(id)))throw new BusinessException("ITEM_NOT_TODAY","条目不属于今日计划",422);
        Instant now=Instant.now(clock);
        jdbc.update("UPDATE learning_items SET solution_text=?,status='completed',completed_at=?,updated_at=? WHERE id=? AND status='pending'",text,now,now.truncatedTo(ChronoUnit.MICROS),id);
        int completed=jdbc.queryForObject("SELECT COUNT(*) FROM learning_items WHERE task_id=? AND status='completed' AND completed_at >= ? AND completed_at < ?",Integer.class,taskId,date.atStartOfDay(zone).toInstant(),date.plusDays(1).atStartOfDay(zone).toInstant());
        int plannedCount=Math.min(task.dailyTargetCount(),jdbc.queryForObject("SELECT COUNT(*) FROM learning_items WHERE task_id=? AND status<>'completed'",Integer.class,taskId)+completed);
        String status=completed>=plannedCount?"completed":"partial";
        upsertCheckin(taskId,date,status,plannedCount,completed);
        CompletionResponse response=new CompletionResponse(getOwned(u,id),plannedCount,completed,jdbc.queryForObject("SELECT COUNT(*) FROM learning_items WHERE task_id=? AND status='completed'",Integer.class,taskId),status);
        idempotency.complete(u,taskId,"complete",key,response);
        return response;
    }

    /** 撤销完成：保留题解；仅当今日为任务计划日时回退当日打卡（跨日事实归属由 S6 补齐）。锁序同 complete：先 tasks 行 X 锁再写幂等键。 */
    @Transactional public CompletionResponse reopen(String u,String id,String key){
        ItemView existing=getOwned(u,id); String taskId=existing.taskId();
        TaskView task=ownedTask(u,taskId);
        lockTask(u,taskId);
        CompletionResponse replay=idempotency.begin(u,taskId,"reopen",key,Map.of("itemId",id),CompletionResponse.class); if(replay!=null)return replay;
        existing=lockItem(u,id);
        if(!"completed".equals(existing.status()))throw new BusinessException("ITEM_NOT_COMPLETED","条目尚未完成",409);
        ZoneId zone=ZoneId.of(task.timezone()); LocalDate date=LocalDate.now(clock.withZone(zone));
        jdbc.update("UPDATE learning_items SET status='pending',completed_at=NULL,updated_at=? WHERE id=? AND status='completed'",Instant.now(clock).truncatedTo(ChronoUnit.MICROS),id);
        int completed=0,plannedCount=0; String checkinStatus="partial"; boolean touchesToday="active".equals(task.status())&&!date.isBefore(task.startDate())&&(task.endDate()==null||!date.isAfter(task.endDate()));
        if(touchesToday){
            completed=jdbc.queryForObject("SELECT COUNT(*) FROM learning_items WHERE task_id=? AND status='completed' AND completed_at >= ? AND completed_at < ?",Integer.class,taskId,date.atStartOfDay(zone).toInstant(),date.plusDays(1).atStartOfDay(zone).toInstant());
            plannedCount=Math.min(task.dailyTargetCount(),jdbc.queryForObject("SELECT COUNT(*) FROM learning_items WHERE task_id=?",Integer.class,taskId));
            checkinStatus=completed>=plannedCount?"completed":"partial";
            upsertCheckin(taskId,date,checkinStatus,plannedCount,completed);
        } else {
            Integer existingCompleted=jdbc.queryForObject("SELECT completed_count FROM checkins WHERE task_id=? AND checkin_date=?",Integer.class,taskId,date);
            checkinStatus=existingCompleted==null?"missed":"partial";
        }
        CompletionResponse response=new CompletionResponse(getOwned(u,id),plannedCount,completed,jdbc.queryForObject("SELECT COUNT(*) FROM learning_items WHERE task_id=? AND status='completed'",Integer.class,taskId),checkinStatus);
        idempotency.complete(u,taskId,"reopen",key,response);
        return response;
    }

    private void upsertCheckin(String taskId,LocalDate date,String status,int planned,int completed){ Instant now=Instant.now(clock).truncatedTo(ChronoUnit.MICROS); jdbc.update("INSERT INTO checkins (id,task_id,checkin_date,status,planned_count,completed_count,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE status=VALUES(status),planned_count=VALUES(planned_count),completed_count=VALUES(completed_count),updated_at=VALUES(updated_at)",UUID.randomUUID().toString(),taskId,date,status,planned,completed,now,now); }
    private void lockTask(String userId,String taskId){ jdbc.query("SELECT id FROM tasks WHERE id=? AND user_id=? FOR UPDATE",r->{if(!r.next())throw new BusinessException("TASK_NOT_FOUND","任务不存在",404);return null;},taskId,userId); }
    private ItemView lockItem(String userId,String itemId){ ItemView item=jdbc.query("SELECT li.* FROM learning_items li JOIN tasks t ON t.id=li.task_id WHERE li.id=? AND t.user_id=? FOR UPDATE",r->r.next()?map(r):null,itemId,userId);if(item==null)throw new BusinessException("ITEM_NOT_FOUND","条目不存在",404);return item; }

    private TaskView ownedTask(String u,String t){return tasks.get(u,t);} private ItemView getOwned(String u,String id){ItemView x=jdbc.query("SELECT li.* FROM learning_items li JOIN tasks t ON t.id=li.task_id WHERE li.id=? AND t.user_id=?",r->r.next()?map(r):null,id,u);if(x==null)throw new BusinessException("ITEM_NOT_FOUND","条目不存在",404);return x;} private int next(String t){Integer n=jdbc.queryForObject("SELECT COALESCE(MAX(sort_order),0)+1 FROM learning_items WHERE task_id=?",Integer.class,t);return n==null?1:n;} private ItemView mapRow(ResultSet r)throws SQLException{return new ItemView(r.getString("id"),r.getString("task_id"),r.getString("title"),r.getString("content"),r.getString("analysis"),r.getString("external_url"),r.getInt("sort_order"),r.getString("status"),r.getString("solution_text"),r.getTimestamp("completed_at")==null?null:r.getTimestamp("completed_at").toInstant());} private ItemView map(ResultSet r,int n)throws SQLException{return mapRow(r);} private ItemView map(ResultSet r)throws SQLException{return mapRow(r);} private void title(String s){if(s==null||s.isBlank()||s.trim().length()>255)throw val("标题无效");} private BusinessException val(String s){return new BusinessException("VALIDATION_ERROR",s,422);}
}
